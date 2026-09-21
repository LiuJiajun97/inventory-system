package com.company.inventory.service.impl;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.stock.StockFreezeDTO;
import com.company.inventory.model.dto.stock.StockUnfreezeDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.stock.StockFreezeLogDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockFreezeLogMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.FreezeLogQuery;
import com.company.inventory.model.vo.stock.FreezeLogVO;
import com.company.inventory.service.StockFreezeService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存冻结服务实现(V26:仓+批次粒度,冻结只拦出不拦入)。
 *
 * <p>冻结/解冻都是对 stock 表 frozen 列的批量置位(该仓该批次下所有库位行一起),
 * 纯追加写 stock_freeze_log 留痕;拦截逻辑不在本类(出库候选 SQL 加 AND NOT frozen,
 * 手动指定批次在 StockCoreService 出库前校验)。</p>
 *
 * @author inventory
 */
@Service
public class StockFreezeServiceImpl implements StockFreezeService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(StockFreezeServiceImpl.class);

    /** 冻结操作码。 */
    private static final String ACTION_FREEZE = "freeze";

    /** 解冻操作码。 */
    private static final String ACTION_UNFREEZE = "unfreeze";

    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 批次 Mapper。 */
    private final BatchMapper batchMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMapper;
    /** 库存 Mapper。 */
    private final StockMapper stockMapper;
    /** 冻结审计流水 Mapper。 */
    private final StockFreezeLogMapper freezeLogMapper;

    /**
     * 构造服务。
     *
     * @param warehouseMapper 仓库 Mapper
     * @param batchMapper     批次 Mapper
     * @param itemMapper      物品 Mapper
     * @param stockMapper     库存 Mapper
     * @param freezeLogMapper 冻结审计流水 Mapper
     */
    public StockFreezeServiceImpl(WarehouseMapper warehouseMapper, BatchMapper batchMapper,
            ItemMapper itemMapper, StockMapper stockMapper, StockFreezeLogMapper freezeLogMapper) {
        this.warehouseMapper = warehouseMapper;
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.stockMapper = stockMapper;
        this.freezeLogMapper = freezeLogMapper;
    }

    /**
     * 冻结批次(整仓+批次,幂等语义:已冻结拒绝)。
     *
     * @param dto      入参
     * @param username 操作人
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(StockFreezeDTO dto, String username) {
        requireWarehouse(dto.warehouseId());
        List<StockDO> rows = requireFrozenTargetRows(dto.warehouseId(), dto.batchNo());
        if (rows.stream().anyMatch(r -> Boolean.TRUE.equals(r.getFrozen()))) {
            throw new BizException("批次已冻结: " + dto.batchNo());
        }
        int updated = stockMapper.update(null, new LambdaUpdateWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, dto.warehouseId())
                .in(StockDO::getBatchId, rows.stream().map(StockDO::getBatchId).toList())
                .set(StockDO::getFrozen, true));
        writeLogs(dto.warehouseId(), rows, ACTION_FREEZE, dto.reason(), username);
        LOGGER.info("库存冻结: 仓库={}, 批次={}, 行数={}, 操作人={}",
                dto.warehouseId(), dto.batchNo(), updated, username);
    }

    /**
     * 解冻批次(整仓+批次,幂等语义:未冻结拒绝)。
     *
     * @param dto      入参
     * @param username 操作人
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(StockUnfreezeDTO dto, String username) {
        requireWarehouse(dto.warehouseId());
        List<StockDO> rows = requireFrozenTargetRows(dto.warehouseId(), dto.batchNo());
        if (rows.stream().noneMatch(r -> Boolean.TRUE.equals(r.getFrozen()))) {
            throw new BizException("批次未冻结: " + dto.batchNo());
        }
        int updated = stockMapper.update(null, new LambdaUpdateWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, dto.warehouseId())
                .in(StockDO::getBatchId, rows.stream().map(StockDO::getBatchId).toList())
                .set(StockDO::getFrozen, false));
        writeLogs(dto.warehouseId(), rows, ACTION_UNFREEZE, null, username);
        LOGGER.info("库存解冻: 仓库={}, 批次={}, 行数={}, 操作人={}",
                dto.warehouseId(), dto.batchNo(), updated, username);
    }

    /**
     * 冻结记录分页查询(时间倒序,带物品编码/名称与批次号回填)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<FreezeLogVO> logs(FreezeLogQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();

        LambdaQueryWrapper<StockFreezeLogDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getWarehouseId() != null) {
            wrapper.eq(StockFreezeLogDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getBatchNo())) {
            List<Long> batchIds = batchMapper.selectList(new LambdaQueryWrapper<BatchDO>()
                    .like(BatchDO::getBatchNo, query.getBatchNo().trim()))
                    .stream().map(BatchDO::getId).toList();
            if (batchIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, page, pageSize);
            }
            wrapper.in(StockFreezeLogDO::getBatchId, batchIds);
        }
        wrapper.orderByDesc(StockFreezeLogDO::getId);

        Page<StockFreezeLogDO> pageResult = freezeLogMapper.selectPage(
                Page.of(page, pageSize), wrapper);
        List<StockFreezeLogDO> rows = pageResult.getRecords();

        Set<Long> itIds = rows.stream().map(StockFreezeLogDO::getItemId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> baIds = rows.stream().map(StockFreezeLogDO::getBatchId)
                .collect(Collectors.toCollection(HashSet::new));
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, ItemDO> itMap = (itIds.isEmpty() ? List.<ItemDO>of()
                : itemMapper.selectByIds(itIds)).stream()
                .collect(Collectors.toMap(ItemDO::getId, Function.identity()));
        Map<Long, BatchDO> baMap = (baIds.isEmpty() ? List.<BatchDO>of()
                : batchMapper.selectByIds(baIds)).stream()
                .collect(Collectors.toMap(BatchDO::getId, Function.identity()));

        List<FreezeLogVO> result = new ArrayList<>();
        for (StockFreezeLogDO row : rows) {
            ItemDO item = itMap.get(row.getItemId());
            BatchDO batch = baMap.get(row.getBatchId());
            result.add(new FreezeLogVO(row.getId(), row.getWarehouseId(), row.getBatchId(),
                    row.getItemId(), row.getAction(), row.getReason(), row.getOperator(),
                    row.getCreatedAt(),
                    item == null ? null : item.getItemCode(),
                    item == null ? null : item.getItemName(),
                    batch == null ? null : batch.getBatchNo()));
        }
        return PageResult.of(result, pageResult.getTotal(), page, pageSize);
    }

    /**
     * 查批次(批次号跨物品可能多条),取该仓下有库存行的目标行集合。
     *
     * @param warehouseId 仓库 ID
     * @param batchNo     批次号
     * @return 该仓+批次下的库存行(非空,否则 400)
     */
    private List<StockDO> requireFrozenTargetRows(long warehouseId, String batchNo) {
        List<BatchDO> batches = batchMapper.selectList(new LambdaQueryWrapper<BatchDO>()
                .eq(BatchDO::getBatchNo, batchNo.trim()));
        if (batches.isEmpty()) {
            throw new BizException("批次不存在: " + batchNo);
        }
        List<StockDO> rows = stockMapper.selectList(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .in(StockDO::getBatchId, batches.stream().map(BatchDO::getId).toList()));
        if (rows.isEmpty()) {
            throw new BizException("该仓无此批次的库存,无法冻结: " + batchNo);
        }
        return rows;
    }

    /**
     * 校验仓库存在。
     *
     * @param warehouseId 仓库 ID
     */
    private void requireWarehouse(long warehouseId) {
        WarehouseDO warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BizException("仓库不存在: id=" + warehouseId);
        }
    }

    /**
     * 写冻结审计流水(每个 仓+批次+物品 组合一条,纯追加)。
     *
     * @param warehouseId 仓库 ID
     * @param rows        目标库存行
     * @param action      操作:freeze / unfreeze
     * @param reason      原因(解冻可空)
     * @param username    操作人
     */
    private void writeLogs(long warehouseId, List<StockDO> rows, String action,
            String reason, String username) {
        Set<String> seen = new HashSet<>();
        for (StockDO row : rows) {
            String key = row.getBatchId() + "-" + row.getItemId();
            if (!seen.add(key)) {
                continue;
            }
            StockFreezeLogDO log = new StockFreezeLogDO();
            log.setWarehouseId(warehouseId);
            log.setBatchId(row.getBatchId());
            log.setItemId(row.getItemId());
            log.setAction(action);
            log.setReason(reason);
            log.setOperator(username);
            freezeLogMapper.insert(log);
        }
    }

}
