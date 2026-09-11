package com.company.inventory.service.impl;

import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.location.LocationDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.stock.StockTransactionDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.LocationMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.StockTransactionMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.StockQuery;
import com.company.inventory.model.query.TransactionQuery;
import com.company.inventory.service.StockQueryService;
import com.company.inventory.model.vo.stock.BatchVO;
import com.company.inventory.model.vo.item.ItemVO;
import com.company.inventory.model.vo.location.LocationVO;
import com.company.inventory.model.vo.stock.StockVO;
import com.company.inventory.model.vo.stock.TransactionVO;
import com.company.inventory.model.vo.stock.TxBatchVO;
import com.company.inventory.model.vo.stock.TxItemVO;
import com.company.inventory.model.vo.stock.TxWarehouseVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

















































import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
























import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存查询服务实现(Stock 表无 relation,按 id 集合手动 join,与 Fastify 版一致)。
 *
 * @author inventory
 */
@Service
public class StockQueryServiceImpl implements StockQueryService {

    /** 无批次/无库位的占位值。 */
    private static final long NO_ID = 0L;

    /** 库存 Mapper。 */
    private final StockMapper stockMapper;
    /** 流水 Mapper。 */
    private final StockTransactionMapper transactionMapper;
    /** 批次 Mapper。 */
    private final BatchMapper batchMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMapper;
    /** 库位 Mapper。 */
    private final LocationMapper locationMapper;

    /**
     * 构造服务。
     *
     * @param stockMapper       库存 Mapper
     * @param transactionMapper 流水 Mapper
     * @param batchMapper       批次 Mapper
     * @param warehouseMapper   仓库 Mapper
     * @param itemMapper        物品 Mapper
     * @param locationMapper    库位 Mapper
     */
    public StockQueryServiceImpl(StockMapper stockMapper, StockTransactionMapper transactionMapper,
                                 BatchMapper batchMapper, WarehouseMapper warehouseMapper,
                                 ItemMapper itemMapper, LocationMapper locationMapper) {
        this.stockMapper = stockMapper;
        this.transactionMapper = transactionMapper;
        this.batchMapper = batchMapper;
        this.warehouseMapper = warehouseMapper;
        this.itemMapper = itemMapper;
        this.locationMapper = locationMapper;
    }

    /**
     * 库存分页查询。
     *
     * <p>数量过滤下推到 SQL(quantity 非空且非 0),保证 count 与分页均在数据库层完成;
     * 当前页数据再手动 join 仓库/物品/批次/库位(与 Fastify 版一致)。</p>
     *
     * @param query 查询条件(warehouseId/itemKeyword/batchNo/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<StockVO> queryStock(StockQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();

        List<Long> itemIds = null;
        if (StringUtils.hasText(query.getItemKeyword())) {
            String like = query.getItemKeyword().trim();
            List<ItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<ItemDO>()
                    .and(w -> w.like(ItemDO::getItemCode, like)
                            .or().like(ItemDO::getItemName, like)));
            itemIds = items.stream().map(ItemDO::getId).toList();
            if (itemIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, page, pageSize);
            }
        }

        List<Long> batchIds = null;
        if (StringUtils.hasText(query.getBatchNo())) {
            List<BatchDO> batches = batchMapper.selectList(new LambdaQueryWrapper<BatchDO>()
                    .like(BatchDO::getBatchNo, query.getBatchNo().trim()));
            batchIds = batches.stream().map(BatchDO::getId).toList();
            if (batchIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, page, pageSize);
            }
        }

        LambdaQueryWrapper<StockDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getWarehouseId() != null) {
            wrapper.eq(StockDO::getWarehouseId, query.getWarehouseId());
        }
        if (itemIds != null) {
            wrapper.in(StockDO::getItemId, itemIds);
        }
        if (batchIds != null) {
            wrapper.in(StockDO::getBatchId, batchIds);
        }
        // 与原内存过滤等价:quantity 非空且非 0 的行才返回(下推到 SQL,支持真实分页)
        wrapper.isNotNull(StockDO::getQuantity).ne(StockDO::getQuantity, BigDecimal.ZERO);
        wrapper.orderByAsc(StockDO::getWarehouseId).orderByAsc(StockDO::getItemId);
        Page<StockDO> pageResult = stockMapper.selectPage(
                Page.of(page, pageSize), wrapper);
        List<StockDO> rows = pageResult.getRecords();

        Set<Long> whIds = rows.stream().map(StockDO::getWarehouseId).collect(Collectors.toCollection(HashSet::new));
        Set<Long> itIds = rows.stream().map(StockDO::getItemId).collect(Collectors.toCollection(HashSet::new));
        Set<Long> baIds = rows.stream().map(StockDO::getBatchId)
                .filter(id -> !id.equals(NO_ID)).collect(Collectors.toCollection(HashSet::new));
        Set<Long> loIds = rows.stream().map(StockDO::getLocationId)
                .filter(id -> !id.equals(NO_ID)).collect(Collectors.toCollection(HashSet::new));

        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"(PG 语法错误)
        Map<Long, WarehouseVO> whMap = toIdMap(whIds.isEmpty() ? List.of() : warehouseMapper.selectByIds(whIds),
                WarehouseDO::getId, this::toWarehouseVO);
        Map<Long, ItemVO> itMap = toIdMap(itIds.isEmpty() ? List.of() : itemMapper.selectByIds(itIds),
                ItemDO::getId, this::toItemVO);
        Map<Long, BatchVO> baMap = toIdMap(baIds.isEmpty() ? List.of() : batchMapper.selectByIds(baIds),
                BatchDO::getId, this::toBatchVO);
        Map<Long, LocationVO> loMap = toIdMap(loIds.isEmpty() ? List.of() : locationMapper.selectByIds(loIds),
                LocationDO::getId, this::toLocationVO);

        List<StockVO> result = new ArrayList<>();
        for (StockDO row : rows) {
            if (row.getQuantity() == null || row.getQuantity().signum() == 0) {
                continue;
            }
            BatchVO batch = row.getBatchId().equals(NO_ID) ? null : baMap.get(row.getBatchId());
            LocationVO location = row.getLocationId().equals(NO_ID) ? null : loMap.get(row.getLocationId());
            BigDecimal qty = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
            BigDecimal pre = row.getPreAllocatedQty() == null ? BigDecimal.ZERO : row.getPreAllocatedQty();
            result.add(new StockVO(row.getId(), row.getWarehouseId(), row.getItemId(),
                    row.getBatchId(), row.getLocationId(), QtyUtils.toContractString(qty),
                    QtyUtils.toContractString(pre), QtyUtils.toContractString(qty.subtract(pre)),
                    whMap.get(row.getWarehouseId()), itMap.get(row.getItemId()), batch, location));
        }
        return PageResult.of(result, pageResult.getTotal(), page, pageSize);
    }

    /**
     * 流水分页查询。
     *
     * @param query 查询条件(warehouseId/itemId/from/to/bizCode/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<TransactionVO> queryTransactions(TransactionQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();
        LocalDateTime from = DateRangeSupport.parseDateTimeStart(query.getFrom(), "开始时间");
        LocalDateTime to = DateRangeSupport.parseDateTimeEnd(query.getTo(), "结束时间");

        LambdaQueryWrapper<StockTransactionDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getWarehouseId() != null) {
            wrapper.eq(StockTransactionDO::getWarehouseId, query.getWarehouseId());
        }
        if (query.getItemId() != null) {
            wrapper.eq(StockTransactionDO::getItemId, query.getItemId());
        }
        if (StringUtils.hasText(query.getBizCode())) {
            wrapper.eq(StockTransactionDO::getBizCode, query.getBizCode());
        }
        if (from != null || to != null) {
            if (from != null) {
                wrapper.ge(StockTransactionDO::getCreatedAt, from);
            }
            if (to != null) {
                wrapper.le(StockTransactionDO::getCreatedAt, to);
            }
        }
        wrapper.orderByDesc(StockTransactionDO::getId);

        Page<StockTransactionDO> result = transactionMapper.selectPage(Page.of(page, pageSize), wrapper);
        List<StockTransactionDO> rows = result.getRecords();

        Set<Long> whIds = rows.stream().map(StockTransactionDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> itIds = rows.stream().map(StockTransactionDO::getItemId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> baIds = rows.stream().map(StockTransactionDO::getBatchId)
                .filter(id -> !id.equals(NO_ID)).collect(Collectors.toCollection(HashSet::new));

        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, TxWarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId,
                        w -> new TxWarehouseVO(w.getId(), w.getWarehouseName())));
        Map<Long, TxItemVO> itMap = (itIds.isEmpty() ? List.<ItemDO>of()
                : itemMapper.selectByIds(itIds)).stream()
                .collect(Collectors.toMap(ItemDO::getId,
                        i -> new TxItemVO(i.getId(), i.getItemCode(), i.getItemName())));
        Map<Long, TxBatchVO> baMap = (baIds.isEmpty() ? List.<BatchDO>of()
                : batchMapper.selectByIds(baIds)).stream()
                .collect(Collectors.toMap(BatchDO::getId,
                        b -> new TxBatchVO(b.getId(), b.getBatchNo())));

        List<TransactionVO> voList = new ArrayList<>();
        for (StockTransactionDO row : rows) {
            TxBatchVO batch = row.getBatchId().equals(NO_ID) ? null : baMap.get(row.getBatchId());
            voList.add(new TransactionVO(row.getId(), row.getWarehouseId(), row.getItemId(),
                    row.getBatchId(), row.getLocationId(),
                    QtyUtils.toContractString(row.getChangeQty()), QtyUtils.toContractString(row.getAfterQty()),
                    row.getBizCode(), row.getDocNo(), row.getOperator(), row.getCreatedAt(),
                    whMap.get(row.getWarehouseId()), itMap.get(row.getItemId()), batch));
        }
        return PageResult.of(voList, result.getTotal(), page, pageSize);
    }


    /**
     * 实体列表转 ID→VO 映射。
     *
     * @param entities 实体列表(可空)
     * @param idGetter 取 ID 函数
     * @param toVO     实体转 VO 函数
     * @param <E>      实体类型
     * @param <T>      VO 类型
     * @return 映射(永不为 null)
     */
    private <E, T> Map<Long, T> toIdMap(List<E> entities, Function<E, Long> idGetter, Function<E, T> toVO) {
        if (entities == null || entities.isEmpty()) {
            return Map.of();
        }
        return entities.stream().collect(Collectors.toMap(idGetter, toVO));
    }

    /**
     * 实体转仓库 VO。
     *
     * @param warehouse 实体
     * @return VO
     */
    private WarehouseVO toWarehouseVO(WarehouseDO warehouse) {
        return new WarehouseVO(warehouse.getId(), warehouse.getWarehouseCode(),
                warehouse.getWarehouseName(), warehouse.getWarehouseType(),
                warehouse.getEnableBatch(), warehouse.getEnableExpiry(),
                warehouse.getEnableSerial(), warehouse.getEnableLocation(),
                warehouse.getStatus(), warehouse.getCreatedAt());
    }

    /**
     * 实体转物品 VO。
     *
     * @param item 实体
     * @return VO
     */
    private ItemVO toItemVO(ItemDO item) {
        return new ItemVO(item.getId(), item.getItemCode(), item.getItemName(),
                item.getUnit(), item.getSpec(), item.getAttributes(),
                item.getStatus(), item.getCreatedAt(),
                item.getCategory(), item.getMinStock(), item.getDefaultTaxRate());
    }

    /**
     * 实体转批次 VO。
     *
     * @param batch 实体
     * @return VO
     */
    private BatchVO toBatchVO(BatchDO batch) {
        return new BatchVO(batch.getId(), batch.getItemId(), batch.getBatchNo(),
                batch.getProductionDate(), batch.getExpiryDate(), batch.getSupplier(), batch.getStatus());
    }

    /**
     * 实体转库位 VO。
     *
     * @param location 实体
     * @return VO
     */
    private LocationVO toLocationVO(LocationDO location) {
        return new LocationVO(location.getId(), location.getWarehouseId(),
                location.getLocationCode(), location.getLocationName());
    }
}
