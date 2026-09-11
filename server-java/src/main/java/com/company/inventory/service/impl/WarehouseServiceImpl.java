package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.warehouse.WarehouseCreateDTO;
import com.company.inventory.model.dto.warehouse.WarehouseUpdateDTO;
import com.company.inventory.model.entity.location.LocationDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.LocationMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.WarehouseQuery;
import com.company.inventory.service.WarehouseService;
import com.company.inventory.model.vo.warehouse.WarehouseVO;















import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;







import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 仓库服务实现。
 *
 * @author inventory
 */
@Service
public class WarehouseServiceImpl implements WarehouseService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(WarehouseServiceImpl.class);

    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;

    /** 库位 Mapper(用于 enableLocation 关闭前的占用校验)。 */
    private final LocationMapper locationMapper;

    /** 库存 Mapper(用于 enableLocation 关闭前的库位库存校验 + 停用前的余额校验)。 */
    private final StockMapper stockMapper;

    /** 序列号 Mapper(用于 enableSerial 关闭前的台账校验)。 */
    private final SerialMapper serialMapper;

    /**
     * 构造服务。
     *
     * @param warehouseMapper 仓库 Mapper
     * @param locationMapper  库位 Mapper
     * @param stockMapper     库存 Mapper
     * @param serialMapper    序列号 Mapper
     */
    public WarehouseServiceImpl(WarehouseMapper warehouseMapper, LocationMapper locationMapper,
            StockMapper stockMapper, SerialMapper serialMapper) {
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.stockMapper = stockMapper;
        this.serialMapper = serialMapper;
    }

    /**
     * 仓库分页列表(按 id 升序)。
     *
     * @param query 查询条件(page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<WarehouseVO> list(WarehouseQuery query) {
        LambdaQueryWrapper<WarehouseDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String like = query.getKeyword().trim();
            wrapper.and(w -> w.like(WarehouseDO::getWarehouseCode, like)
                    .or().like(WarehouseDO::getWarehouseName, like));
        }
        if (StringUtils.hasText(query.getWarehouseType())) {
            wrapper.eq(WarehouseDO::getWarehouseType, query.getWarehouseType().trim());
        }
        wrapper.orderByAsc(WarehouseDO::getId);
        Page<WarehouseDO> page = warehouseMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<WarehouseVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 仓库详情。
     *
     * @param id 仓库 ID
     * @return 仓库
     */
    @Override
    public WarehouseVO get(long id) {
        WarehouseDO warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw BizException.notFound("仓库不存在");
        }
        return toVO(warehouse);
    }

    /**
     * 新建仓库。
     *
     * @param dto 入参
     * @return 新建仓库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarehouseVO create(WarehouseCreateDTO dto) {
        Long exist = warehouseMapper.selectCount(new LambdaQueryWrapper<WarehouseDO>()
                .eq(WarehouseDO::getWarehouseCode, dto.warehouseCode()));
        if (exist != null && exist > 0) {
            throw new BizException("仓库编码已存在", ErrorCode.WAREHOUSE_CODE_DUP, ErrorCode.HTTP_BAD_REQUEST);
        }
        WarehouseDO warehouse = new WarehouseDO();
        warehouse.setWarehouseCode(dto.warehouseCode());
        warehouse.setWarehouseName(dto.warehouseName());
        warehouse.setWarehouseType(dto.warehouseType());
        warehouse.setEnableBatch(Boolean.TRUE.equals(dto.enableBatch()));
        warehouse.setEnableExpiry(Boolean.TRUE.equals(dto.enableExpiry()));
        warehouse.setEnableSerial(Boolean.TRUE.equals(dto.enableSerial()));
        warehouse.setEnableLocation(Boolean.TRUE.equals(dto.enableLocation()));
        warehouseMapper.insert(warehouse);
        LOGGER.info("新建仓库: code={}, name={}, 编码重复检查通过", dto.warehouseCode(), dto.warehouseName());
        return toVO(warehouse);
    }

    /**
     * 实体转 VO。
     *
     * @param warehouse 实体
     * @return VO
     */
    private WarehouseVO toVO(WarehouseDO warehouse) {
        return new WarehouseVO(warehouse.getId(), warehouse.getWarehouseCode(),
                warehouse.getWarehouseName(), warehouse.getWarehouseType(),
                warehouse.getEnableBatch(), warehouse.getEnableExpiry(),
                warehouse.getEnableSerial(), warehouse.getEnableLocation(),
                warehouse.getStatus(), warehouse.getCreatedAt());
    }

    /**
     * 编辑仓库(仅 admin,编码不可改,至少一个字段非空)。
     *
     * <p>防数据不一致校验:
     * 1) enableLocation 1→0 时,该仓存在库位或库位库存行 → 400;
     * 2) enableSerial 1→0 时,该仓存在序列号台账 → 400;
     * 3) status 1→0 时,该仓仍有 quantity&gt;0 或 preAllocatedQty&gt;0 → 400。
     * enableExpiry 1→0 直接放行,历史批次数据保留不受影响。</p>
     *
     * @param id  仓库 ID
     * @param dto 入参
     * @return 更新后的仓库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarehouseVO update(long id, WarehouseUpdateDTO dto) {
        WarehouseDO warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw BizException.notFound("仓库不存在");
        }
        if (Boolean.FALSE.equals(dto.enableLocation())
                && Boolean.TRUE.equals(warehouse.getEnableLocation())) {
            assertLocationClosed(id);
        }
        if (Boolean.FALSE.equals(dto.enableSerial())
                && Boolean.TRUE.equals(warehouse.getEnableSerial())) {
            assertSerialClosed(id);
        }
        if (Integer.valueOf(0).equals(dto.status())
                && Integer.valueOf(1).equals(warehouse.getStatus())) {
            assertNoActiveStock(id);
        }
        boolean changed = false;
        if (dto.warehouseName() != null) {
            warehouse.setWarehouseName(dto.warehouseName());
            changed = true;
        }
        if (dto.warehouseType() != null) {
            warehouse.setWarehouseType(dto.warehouseType());
            changed = true;
        }
        if (dto.enableBatch() != null) {
            warehouse.setEnableBatch(dto.enableBatch());
            changed = true;
        }
        if (dto.enableExpiry() != null) {
            warehouse.setEnableExpiry(dto.enableExpiry());
            changed = true;
        }
        if (dto.enableSerial() != null) {
            warehouse.setEnableSerial(dto.enableSerial());
            changed = true;
        }
        if (dto.enableLocation() != null) {
            warehouse.setEnableLocation(dto.enableLocation());
            changed = true;
        }
        if (dto.status() != null) {
            warehouse.setStatus(dto.status());
            changed = true;
        }
        if (!changed) {
            throw new BizException("至少提供一个可更新字段",
                    ErrorCode.VALIDATION_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
        warehouseMapper.updateById(warehouse);
        LOGGER.info("编辑仓库: id={}, code={}", id, warehouse.getWarehouseCode());
        return toVO(warehouse);
    }

    /**
     * 校验仓库可关闭库位功能(无库位、无库位库存)。
     *
     * @param warehouseId 仓库 ID
     */
    private void assertLocationClosed(long warehouseId) {
        Long locCount = locationMapper.selectCount(new LambdaQueryWrapper<LocationDO>()
                .eq(LocationDO::getWarehouseId, warehouseId));
        if (locCount != null && locCount > 0) {
            throw new BizException("该仓已有库位,不能关闭库位功能",
                    ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
        Long stockWithLoc = stockMapper.selectCount(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .ne(StockDO::getLocationId, 0L));
        if (stockWithLoc != null && stockWithLoc > 0) {
            throw new BizException("该仓已有库位库存,不能关闭库位功能",
                    ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
    }

    /**
     * 校验仓库可关闭序列号功能(无序列号台账)。
     *
     * @param warehouseId 仓库 ID
     */
    private void assertSerialClosed(long warehouseId) {
        Long serialCount = serialMapper.selectCount(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getWarehouseId, warehouseId));
        if (serialCount != null && serialCount > 0) {
            throw new BizException("该仓已有序列号记录,不能关闭序列号功能",
                    ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
    }

    /**
     * 校验仓库可停用(无余额/无预占)。
     *
     * @param warehouseId 仓库 ID
     */
    private void assertNoActiveStock(long warehouseId) {
        Long active = stockMapper.selectCount(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .and(w -> w.gt(StockDO::getQuantity, java.math.BigDecimal.ZERO)
                        .or().gt(StockDO::getPreAllocatedQty, java.math.BigDecimal.ZERO)));
        if (active != null && active > 0) {
            throw new BizException("该仓仍有库存,不能停用",
                    ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
    }
}
