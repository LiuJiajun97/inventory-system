package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.warehouse.WarehouseCreateDTO;
import com.company.inventory.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.query.WarehouseQuery;
import com.company.inventory.service.WarehouseService;
import com.company.inventory.vo.warehouse.WarehouseVO;















import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;







import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 构造服务。
     *
     * @param warehouseMapper 仓库 Mapper
     */
    public WarehouseServiceImpl(WarehouseMapper warehouseMapper) {
        this.warehouseMapper = warehouseMapper;
    }

    /**
     * 仓库分页列表(按 id 升序)。
     *
     * @param query 查询条件(page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<WarehouseVO> list(WarehouseQuery query) {
        LambdaQueryWrapper<WarehouseDO> wrapper =
                new LambdaQueryWrapper<WarehouseDO>().orderByAsc(WarehouseDO::getId);
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
}
