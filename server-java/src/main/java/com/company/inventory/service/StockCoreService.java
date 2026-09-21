package com.company.inventory.service;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.stock.StockFreezeLogDO;
import com.company.inventory.model.entity.stock.StockTransactionDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.StockFreezeLogMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.StockTransactionMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.model.vo.stock.StockOpResult;







































import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;


















import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存核心服务:入库 / 出库 / FEFO / FIFO / 序列号(对应 server/src/lib/stock-core.ts,规则零弱化)。
 *
 * <p>关键约束:
 * <ol>
 * <li>出库扣减必须是条件 UPDATE(quantity &gt;= qty)原子操作,禁止先查后改,防并发穿仓;</li>
 * <li>整单一个事务:调用方(单据服务)开启事务,本服务方法加入同一事务,任一失败整单回滚;</li>
 * <li>流水只插不改,必带 afterQty(事务内扣减/增加后回读);</li>
 * <li>选批:指定批次 &gt; FEFO(保质期升序,NULL 最后)&gt; FIFO(按批次最近入库流水时间升序);</li>
 * <li>批次/流水查询全部批量执行,禁止循环查库。</li>
 * </ol></p>
 *
 * @author inventory
 */
@Service
public class StockCoreService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(StockCoreService.class);

    /** 无批次/无库位占位值。 */
    private static final long NO_ID = 0L;

    /** 数量整数判定用 1。 */
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final WarehouseMapper warehouseMapper;
    private final ItemMapper itemMapper;
    private final BatchMapper batchMapper;
    private final StockMapper stockMapper;
    private final StockTransactionMapper stockTransactionMapper;
    private final SerialMapper serialMapper;
    /** 冻结审计流水 Mapper(V26:手动指定批次出库前的冻结校验取最近原因)。 */
    private final StockFreezeLogMapper freezeLogMapper;

    /**
     * 构造服务。
     *
     * @param warehouseMapper       仓库 Mapper
     * @param itemMapper            物品 Mapper
     * @param batchMapper           批次 Mapper
     * @param stockMapper           库存 Mapper
     * @param stockTransactionMapper 流水 Mapper
     * @param serialMapper          序列号 Mapper
     * @param freezeLogMapper       冻结审计流水 Mapper(V26)
     */
    public StockCoreService(WarehouseMapper warehouseMapper, ItemMapper itemMapper,
            BatchMapper batchMapper, StockMapper stockMapper,
            StockTransactionMapper stockTransactionMapper, SerialMapper serialMapper,
            StockFreezeLogMapper freezeLogMapper) {
        this.warehouseMapper = warehouseMapper;
        this.itemMapper = itemMapper;
        this.batchMapper = batchMapper;
        this.stockMapper = stockMapper;
        this.stockTransactionMapper = stockTransactionMapper;
        this.serialMapper = serialMapper;
        this.freezeLogMapper = freezeLogMapper;
    }

    /**
     * 入库:批次不存在自动建;启用保质期入库必须带批次;已过期批次拒绝;序列号逐号写入。
     *
     * <p>独立调用时自成事务;被单据服务调用时加入其事务(同一单据整单回滚)。</p>
     *
     * @param request 入库请求
     * @return 操作结果(每行:物品/批次/库位/数量)
     */
    @Transactional(rollbackFor = Exception.class)
    public StockOpResult inbound(StockOpRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BizException("入库行不能为空");
        }
        WarehouseDO warehouse = requireWarehouse(request.getWarehouseId());
        StockOpResult result = new StockOpResult();

        for (StockLine line : request.getLines()) {
            // 按仓库 4 开关校验必填项(规则零弱化)
            validateByWarehouseConfig(warehouse, line, ErrorCode.DIRECTION_INBOUND);

            // 1. 处理批次:不存在则自动建;历史批次缺保质期时用入库行补齐
            long batchId = NO_ID;
            if (hasText(line.getBatchNo())) {
                BatchDO batch = findBatch(line.getItemId(), line.getBatchNo());
                if (batch == null) {
                    batch = new BatchDO();
                    batch.setItemId(line.getItemId());
                    batch.setBatchNo(line.getBatchNo());
                    batch.setProductionDate(line.getProductionDate());
                    batch.setExpiryDate(line.getExpiryDate());
                    batch.setSupplier(line.getSupplier());
                    batch.setStatus(ErrorCode.BATCH_STATUS_ACTIVE);
                    batchMapper.insert(batch);
                } else if (line.getExpiryDate() != null && batch.getExpiryDate() == null) {
                    batch.setExpiryDate(line.getExpiryDate());
                    batchMapper.updateById(batch);
                }
                // 启用保质期的仓库:已过期批次拒绝入库
                if (Boolean.TRUE.equals(warehouse.getEnableExpiry())
                        && batch.getExpiryDate() != null
                        && batch.getExpiryDate().isBefore(LocalDate.now())) {
                    throw new BizException("批次 " + line.getBatchNo() + " 已过期,不允许入库");
                }
                batchId = batch.getId();
            }
            if (Boolean.TRUE.equals(warehouse.getEnableExpiry()) && batchId == NO_ID) {
                throw new BizException("启用保质期的仓库,入库必须指定批次");
            }

            long locationId = line.getLocationId() == null ? NO_ID : line.getLocationId();

            // 2. 同一事务内回读原值,计算变动后余额
            StockDO existing = findStock(request.getWarehouseId(), line.getItemId(), batchId, locationId);
            BigDecimal beforeQty = existing == null ? BigDecimal.ZERO : existing.getQuantity();
            BigDecimal afterQty = beforeQty.add(line.getQty());

            // 3. 写流水(只插不改,必带 afterQty)
            insertTransaction(request.getWarehouseId(), line.getItemId(), batchId, locationId,
                    line.getQty(), afterQty, resolveBizCode(request, ErrorCode.BIZ_CODE_INBOUND), request);

            // 4. 更新余额:优先条件累加,行不存在则新建(余额行唯一键,默认 0)
            int updated = stockMapper.incrementStock(
                    request.getWarehouseId(), line.getItemId(), batchId, locationId, line.getQty());
            if (updated == 0) {
                StockDO row = new StockDO();
                row.setWarehouseId(request.getWarehouseId());
                row.setItemId(line.getItemId());
                row.setBatchId(batchId);
                row.setLocationId(locationId);
                row.setQuantity(line.getQty());
                stockMapper.insertStockRow(row);
            }

            // 5. 序列号逐号写入(in_stock)
            if (Boolean.TRUE.equals(warehouse.getEnableSerial())
                    && line.getSerialNos() != null && !line.getSerialNos().isEmpty()) {
                for (String sn : line.getSerialNos()) {
                    // 退货回流:已出库序列号先条件回置入库,失败(影响 0 行)才新建;
                    // 既非 out 又已存在的号撞唯一键,由 DB 约束兜底
                    int back = serialMapper.markBackInStock(line.getItemId(), sn, request.getWarehouseId());
                    if (back > 0) {
                        continue;
                    }
                    SerialDO serial = new SerialDO();
                    serial.setItemId(line.getItemId());
                    serial.setSerialNo(sn);
                    serial.setWarehouseId(request.getWarehouseId());
                    serial.setStatus(ErrorCode.SERIAL_STATUS_IN_STOCK);
                    serial.setInboundTime(LocalDateTime.now());
                    serialMapper.insert(serial);
                }
            }

            result.addRow(new StockOpResult.StockOpRow(
                    line.getItemId(), batchId, locationId, line.getQty()));
            LOGGER.info("入库: 单号={}, 仓库={}, 物品={}, 数量={}, 批次={}, 库位={}, 操作人={}",
                    request.getDocNo(), request.getWarehouseId(), line.getItemId(),
                    QtyUtils.toContractString(line.getQty()), batchId, locationId, request.getOperator());
        }
        return result;
    }

    /**
     * 出库:选批(指定批次 &gt; FEFO &gt; FIFO)+ 条件 UPDATE 原子扣减 + 流水 + 序列号逐号出库。
     *
     * <p>扣减使用条件 UPDATE(quantity &gt;= qty),affected rows=0 即库存不足,整单回滚,
     * 从机制上杜绝并发穿仓。禁止先查后改。</p>
     *
     * @param request 出库请求
     * @return 操作结果(每行:物品/批次/库位/数量)
     */
    @Transactional(rollbackFor = Exception.class)
    public StockOpResult outbound(StockOpRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BizException("出库行不能为空");
        }
        WarehouseDO warehouse = requireWarehouse(request.getWarehouseId());
        long warehouseId = request.getWarehouseId();
        StockOpResult result = new StockOpResult();

        for (StockLine line : request.getLines()) {
            boolean salesShip = request.isSalesShip();
            // 销售发货:库位取自实际预占行,不要求行上指定库位
            validateByWarehouseConfig(warehouse, line, ErrorCode.DIRECTION_OUTBOUND, !salesShip);
            long locationId = line.getLocationId() == null ? NO_ID : line.getLocationId();

            if (salesShip) {
                // 销售发货(方案 §5.2):优先扣预占行并同步释放预占,批次跟随预占行
                List<SalesDeductPiece> pieces =
                        salesDeduct(warehouseId, line.getItemId(), line.getQty());
                for (SalesDeductPiece piece : pieces) {
                    BigDecimal afterQty =
                            piece.after() == null ? BigDecimal.ZERO : piece.after().getQuantity();
                    insertTransaction(warehouseId, line.getItemId(), piece.batchId(),
                            piece.locationId(), piece.qty().abs().negate(), afterQty,
                            resolveBizCode(request, ErrorCode.BIZ_CODE_OUTBOUND), request);
                }
                markSerialsOutbound(warehouse, line, warehouseId);
                result.addRow(new StockOpResult.StockOpRow(line.getItemId(),
                        pieces.get(0).batchId(), pieces.get(0).locationId(), line.getQty()));
                LOGGER.info("销售发货扣预占: 单号={}, 仓库={}, 物品={}, 数量={}, 操作人={}",
                        request.getDocNo(), warehouseId, line.getItemId(),
                        QtyUtils.toContractString(line.getQty()), request.getOperator());
                continue;
            }

            // 1. 选批次
            long batchId = NO_ID;
            if (hasText(line.getBatchNo())) {
                BatchDO batch = findBatch(line.getItemId(), line.getBatchNo());
                if (batch == null) {
                    throw new BizException("批次不存在: " + line.getBatchNo());
                }
                if (Boolean.TRUE.equals(warehouse.getEnableExpiry())
                        && batch.getExpiryDate() != null
                        && batch.getExpiryDate().isBefore(LocalDate.now())) {
                    throw new BizException("批次 " + line.getBatchNo() + " 已过期,不允许出库");
                }
                // V26 冻结校验:该仓+批次存在 frozen=true 的库存行即拒绝出库(原因取最近一条 freeze 流水)
                if (isBatchFrozen(warehouseId, batch.getId())) {
                    throw new BizException("批次 " + line.getBatchNo() + " 已冻结,不允许出库: "
                            + latestFreezeReason(warehouseId, batch.getId()));
                }
                batchId = batch.getId();
            } else if (Boolean.TRUE.equals(warehouse.getEnableExpiry())) {
                // FEFO:保质期最早优先(NULL 排最后)
                batchId = pickBatchFEFO(request.getWarehouseId(), line.getItemId(), locationId);
                if (batchId != NO_ID) {
                    BatchDO picked = batchMapper.selectById(batchId);
                    if (picked != null && picked.getExpiryDate() != null
                            && picked.getExpiryDate().isBefore(LocalDate.now())) {
                        throw new BizException("自动选出的批次 " + picked.getBatchNo() + " 已过期");
                    }
                }
            } else if (Boolean.TRUE.equals(warehouse.getEnableBatch())) {
                // FIFO:按批次最近一笔入库流水时间升序
                batchId = pickBatchFIFO(request.getWarehouseId(), line.getItemId(), locationId);
            }

            // 2. 关键:条件 UPDATE 原子扣减(防穿仓)并原子返回扣后余额,失败即库存不足
            java.math.BigDecimal afterQty = stockMapper.deductStockReturning(
                    request.getWarehouseId(), line.getItemId(), batchId, locationId, line.getQty());
            if (afterQty == null) {
                ItemDO item = itemMapper.selectById(line.getItemId());
                String code = item != null ? item.getItemCode() : String.valueOf(line.getItemId());
                throw new BizException("库存不足:物品" + code + " 可用数量不够,需要 "
                        + QtyUtils.toContractString(line.getQty()));
            }

            // 3. 写流水(出库为负数,必带 afterQty,RETURNING 保证并发下精确)
            insertTransaction(request.getWarehouseId(), line.getItemId(), batchId, locationId,
                    line.getQty().abs().negate(), afterQty,
                    resolveBizCode(request, ErrorCode.BIZ_CODE_OUTBOUND), request);

            // 5. 序列号逐号条件出库(状态 in_stock + 仓库匹配),任一失败整单回滚
            markSerialsOutbound(warehouse, line, warehouseId);

            result.addRow(new StockOpResult.StockOpRow(
                    line.getItemId(), batchId, locationId, line.getQty()));
            LOGGER.info("出库: 单号={}, 仓库={}, 物品={}, 数量={}, 批次={}, 库位={}, 操作人={}",
                    request.getDocNo(), request.getWarehouseId(), line.getItemId(),
                    QtyUtils.toContractString(line.getQty()), batchId, locationId, request.getOperator());
        }
        return result;
    }

    /**
     * FEFO 选批:在指定仓库/物品/库位下,从 quantity&gt;0 且 batchId&gt;0 的余额行中,
     * 选保质期 expiryDate 最早的批次;NULL 保质期排最后;同值按余额行 id 保证确定性。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param locationId  库位 ID
     * @return 批次 ID,无候选返回 0
     */
    private long pickBatchFEFO(long warehouseId, long itemId, long locationId) {
        List<StockDO> stocks = stockMapper.selectActiveStocks(warehouseId, itemId, locationId);
        if (stocks.isEmpty()) {
            return NO_ID;
        }
        List<Long> batchIds = new ArrayList<>();
        for (StockDO s : stocks) {
            batchIds.add(s.getBatchId());
        }
        Map<Long, LocalDate> expiryMap = loadBatchMap(batchIds);
        stocks.sort(Comparator
                .comparing((StockDO s) -> expiryMap.getOrDefault(s.getBatchId(), null) == null)
                .thenComparing(s -> expiryMap.getOrDefault(s.getBatchId(), null),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockDO::getId));
        return stocks.get(0).getBatchId();
    }

    /**
     * FIFO 选批:按每个批次的最近一笔入库流水 createdAt 升序;无流水的批次排最后;
     * 批次的流水时间一次批量查出(禁止循环查库)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param locationId  库位 ID
     * @return 批次 ID,无候选返回 0
     */
    private long pickBatchFIFO(long warehouseId, long itemId, long locationId) {
        List<StockDO> stocks = stockMapper.selectActiveStocks(warehouseId, itemId, locationId);
        if (stocks.isEmpty()) {
            return NO_ID;
        }
        List<Long> batchIds = new ArrayList<>();
        for (StockDO s : stocks) {
            batchIds.add(s.getBatchId());
        }
        Map<Long, LocalDateTime> latestMap = loadLatestInboundTime(warehouseId, itemId, batchIds);
        stocks.sort(Comparator
                .comparing((StockDO s) -> latestMap.get(s.getBatchId()) == null)
                .thenComparing(s -> latestMap.get(s.getBatchId()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockDO::getId));
        return stocks.get(0).getBatchId();
    }

    /**
     * 批量加载批次保质期映射(一次查库)。
     *
     * @param batchIds 批次 ID 集合
     * @return 批次 ID → 到期日映射
     */
    private Map<Long, LocalDate> loadBatchMap(List<Long> batchIds) {
        Map<Long, LocalDate> map = new HashMap<>();
        if (batchIds.isEmpty()) {
            return map;
        }
        List<BatchDO> batches = batchMapper.selectBatchIds(batchIds);
        for (BatchDO b : batches) {
            map.put(b.getId(), b.getExpiryDate());
        }
        return map;
    }

    /**
     * 批量加载每个批次的最近一笔入库流水时间(一次查库)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchIds    批次 ID 集合
     * @return 批次 ID → 最近入库时间映射
     */
    private Map<Long, LocalDateTime> loadLatestInboundTime(
            long warehouseId, long itemId, List<Long> batchIds) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        if (batchIds.isEmpty()) {
            return map;
        }
        List<Map<String, Object>> rows =
                stockMapper.selectLatestInboundTimeByBatch(warehouseId, itemId, batchIds);
        for (Map<String, Object> row : rows) {
            Object batchId = row.get("batchId");
            Object time = row.get("maxCreatedAt");
            if (batchId instanceof Number number && time instanceof LocalDateTime t) {
                map.put(number.longValue(), t);
            }
        }
        return map;
    }

    /**
     * V26:判定仓+批次是否存在冻结库存行(手动指定批次出库前拦截)。
     *
     * @param warehouseId 仓库 ID
     * @param batchId     批次 ID
     * @return true 表示存在 frozen=true 的库存行
     */
    private boolean isBatchFrozen(long warehouseId, long batchId) {
        Long count = stockMapper.selectCount(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .eq(StockDO::getBatchId, batchId)
                .eq(StockDO::getFrozen, true));
        return count != null && count > 0;
    }

    /**
     * V26:取仓+批次最近一条 freeze 流水的原因(出库拦截文案用)。
     *
     * @param warehouseId 仓库 ID
     * @param batchId     批次 ID
     * @return 冻结原因(查不到返回"(无原因)")
     */
    private String latestFreezeReason(long warehouseId, long batchId) {
        StockFreezeLogDO log = freezeLogMapper.selectOne(new LambdaQueryWrapper<StockFreezeLogDO>()
                .eq(StockFreezeLogDO::getWarehouseId, warehouseId)
                .eq(StockFreezeLogDO::getBatchId, batchId)
                .eq(StockFreezeLogDO::getAction, "freeze")
                .orderByDesc(StockFreezeLogDO::getId)
                .last("LIMIT 1"));
        if (log == null || log.getReason() == null || log.getReason().isBlank()) {
            return "(无原因)";
        }
        return log.getReason();
    }

    /**
     * 预占库存(销售订单审批时逐行调用,FEFO 顺序选行,条件 UPDATE 累加 preAllocatedQty)。
     *
     * <p>候选行=仓+物品下 quantity&gt;0 的所有余额行(含无批次行);排序=保质期升序
     * (NULL 排最后)再按行 id;启用保质期仓跳过已过期批次;逐行条件累加
     * (可用量足够才加);合计不足抛业务异常,调用方整单回滚(订单回 draft)。</p>
     *
     * <p>独立调用时自成事务;被单据服务调用时加入其事务。</p>
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param qty         预占数量(正数)
     * @param lineDesc    行描述(用于错误提示,如"行2(物品编码)")
     */
    @Transactional(rollbackFor = Exception.class)
    public void preAlloc(long warehouseId, long itemId, BigDecimal qty, String lineDesc) {
        WarehouseDO warehouse = requireWarehouse(warehouseId);
        List<StockDO> candidates = stockMapper.selectPreAllocCandidates(warehouseId, itemId);
        sortPreAllocCandidates(warehouse, candidates);
        BigDecimal remaining = qty;
        for (StockDO row : candidates) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal available = availableOf(row);
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal n = remaining.min(available);
            if (stockMapper.preAllocRow(row.getId(), n) == 0) {
                continue;
            }
            insertTransaction(warehouseId, itemId, row.getBatchId(), row.getLocationId(),
                    n, row.getQuantity(), ErrorCode.BIZ_CODE_PRE_ALLOC, "(预占)", lineDesc);
            remaining = remaining.subtract(n);
        }
        if (remaining.signum() > 0) {
            throw new BizException("可用库存不足:" + lineDesc + ",仍缺 "
                    + QtyUtils.toContractString(remaining));
        }
        LOGGER.info("预占: 仓库={}, 物品={}, 数量={}", warehouseId, itemId,
                QtyUtils.toContractString(qty));
    }

    /**
     * 释放预占(销售订单关闭/作废时逐行调用,条件 UPDATE 扣减 preAllocatedQty)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param qty         释放数量(正数)
     * @param lineDesc    行描述(用于错误提示)
     */
    @Transactional(rollbackFor = Exception.class)
    public void releasePreAlloc(long warehouseId, long itemId, BigDecimal qty, String lineDesc) {
        requireWarehouse(warehouseId);
        List<StockDO> candidates = stockMapper.selectPreAllocCandidates(warehouseId, itemId);
        BigDecimal remaining = qty;
        for (StockDO row : candidates) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal pre = row.getPreAllocatedQty() == null ? BigDecimal.ZERO : row.getPreAllocatedQty();
            if (pre.signum() <= 0) {
                continue;
            }
            BigDecimal n = remaining.min(pre);
            if (stockMapper.releasePreAllocRow(row.getId(), n) == 0) {
                continue;
            }
            insertTransaction(warehouseId, itemId, row.getBatchId(), row.getLocationId(),
                    n.negate(), row.getQuantity(), ErrorCode.BIZ_CODE_RELEASE_PRE_ALLOC, "(释放预占)", lineDesc);
            remaining = remaining.subtract(n);
        }
        if (remaining.signum() > 0) {
            throw new BizException("预占量不足,释放失败:" + lineDesc + ",仍缺 "
                    + QtyUtils.toContractString(remaining));
        }
    }

    /**
     * 销售发货扣减:逐行扣 preAllocatedQty&gt;0 的余额行(条件 UPDATE,预占量足够才扣),
     * 多行分摊直到扣足;不足抛业务异常整单回滚。可用口径全局统一:
     * available = quantity - preAllocatedQty。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param qty         发货数量(正数)
     * @return 扣减片段(批次/库位/数量/扣后余额行),用于写流水
     */
    private List<SalesDeductPiece> salesDeduct(long warehouseId, long itemId, BigDecimal qty) {
        List<StockDO> candidates = stockMapper.selectPreAllocCandidates(warehouseId, itemId);
        BigDecimal remaining = qty;
        List<SalesDeductPiece> pieces = new ArrayList<>();
        for (StockDO row : candidates) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal pre = row.getPreAllocatedQty() == null ? BigDecimal.ZERO : row.getPreAllocatedQty();
            if (pre.signum() <= 0) {
                continue;
            }
            BigDecimal n = remaining.min(pre);
            if (stockMapper.salesDeductRow(row.getId(), n) == 0) {
                continue;
            }
            StockDO after = findStock(warehouseId, itemId, row.getBatchId(), row.getLocationId());
            pieces.add(new SalesDeductPiece(row.getBatchId(), row.getLocationId(), n, after));
            remaining = remaining.subtract(n);
        }
        if (remaining.signum() > 0) {
            throw new BizException("预占库存不足,发货失败:物品 " + itemId + ",仍缺 "
                    + QtyUtils.toContractString(remaining));
        }
        return pieces;
    }

    /**
     * 调拨(方案 §5.3,拍板点 1 原子一步):同一事务内源仓条件扣减 + 目的仓入库,
     * 批次跟随源批,序列号台账同步改仓库,任一失败整单回滚。
     *
     * <p>request.warehouseId 为目的仓,request.fromWarehouseId 为源仓;
     * 行 locationId=源库位,toLocationId=目的库位。源仓可用量不足任一行即整单失败。</p>
     *
     * @param request 调拨请求
     * @return 操作结果(每行:物品/批次/目的库位/数量)
     */
    @Transactional(rollbackFor = Exception.class)
    public StockOpResult transfer(StockOpRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BizException("调拨行不能为空");
        }
        long fromWarehouseId = request.getFromWarehouseId();
        WarehouseDO fromWarehouse = requireWarehouse(fromWarehouseId);
        WarehouseDO toWarehouse = requireWarehouse(request.getWarehouseId());
        if (fromWarehouseId == request.getWarehouseId()) {
            throw new BizException("调拨源仓与目的仓不能相同");
        }
        StockOpResult result = new StockOpResult();
        for (StockLine line : request.getLines()) {
            validateTransferLine(fromWarehouse, toWarehouse, line);
            long fromLocationId = line.getLocationId() == null ? NO_ID : line.getLocationId();
            long toLocationId = line.getToLocationId() == null ? NO_ID : line.getToLocationId();

            // 1. 源仓选批(FEFO/FIFO,批次跟随源批)
            long batchId = NO_ID;
            if (Boolean.TRUE.equals(fromWarehouse.getEnableExpiry())) {
                batchId = pickBatchFEFO(fromWarehouseId, line.getItemId(), fromLocationId);
            } else if (Boolean.TRUE.equals(fromWarehouse.getEnableBatch())) {
                batchId = pickBatchFIFO(fromWarehouseId, line.getItemId(), fromLocationId);
            }
            if (Boolean.TRUE.equals(fromWarehouse.getEnableExpiry())
                    || Boolean.TRUE.equals(fromWarehouse.getEnableBatch())) {
                if (batchId == NO_ID) {
                    throw new BizException("调拨失败:源仓无可拨批次:物品 " + line.getItemId()
                            + ",需要 " + QtyUtils.toContractString(line.getQty()));
                }
            }

            // 2. 源仓条件扣减(含预占检查,防穿仓)
            int deducted = stockMapper.deductStock(
                    fromWarehouseId, line.getItemId(), batchId, fromLocationId, line.getQty());
            if (deducted == 0) {
                ItemDO item = itemMapper.selectById(line.getItemId());
                String code = item != null ? item.getItemCode() : String.valueOf(line.getItemId());
                throw new BizException("调拨失败:源仓可用量不足:物品 " + code
                        + ",需要 " + QtyUtils.toContractString(line.getQty()));
            }

            // 3. 目的仓入库(余额行不存在则新建)
            int updated = stockMapper.incrementStock(
                    request.getWarehouseId(), line.getItemId(), batchId, toLocationId, line.getQty());
            if (updated == 0) {
                StockDO row = new StockDO();
                row.setWarehouseId(request.getWarehouseId());
                row.setItemId(line.getItemId());
                row.setBatchId(batchId);
                row.setLocationId(toLocationId);
                row.setQuantity(line.getQty());
                stockMapper.insertStockRow(row);
            }

            // 4. 流水:源仓 transfer_out(负)/目的仓 transfer_in(正)
            StockDO afterFrom = findStock(fromWarehouseId, line.getItemId(), batchId, fromLocationId);
            BigDecimal afterFromQty = afterFrom == null ? BigDecimal.ZERO : afterFrom.getQuantity();
            insertTransaction(fromWarehouseId, line.getItemId(), batchId, fromLocationId,
                    line.getQty().abs().negate(), afterFromQty, ErrorCode.BIZ_CODE_TRANSFER_OUT, request);
            StockDO afterTo = findStock(request.getWarehouseId(), line.getItemId(), batchId, toLocationId);
            BigDecimal afterToQty = afterTo == null ? BigDecimal.ZERO : afterTo.getQuantity();
            insertTransaction(request.getWarehouseId(), line.getItemId(), batchId, toLocationId,
                    line.getQty(), afterToQty, ErrorCode.BIZ_CODE_TRANSFER_IN, request);

            // 5. 序列号台账同步改仓库(同事务)
            if (Boolean.TRUE.equals(fromWarehouse.getEnableSerial())) {
                for (String sn : line.getSerialNos()) {
                    int moved = serialMapper.transferWarehouse(
                            line.getItemId(), sn, fromWarehouseId, request.getWarehouseId());
                    if (moved == 0) {
                        throw new BizException("调拨失败:序列号 " + sn + " 不在源仓或已出库,整单回滚");
                    }
                }
            }

            result.addRow(new StockOpResult.StockOpRow(
                    line.getItemId(), batchId, toLocationId, line.getQty()));
            LOGGER.info("调拨: 单号={}, 源仓={}, 目的仓={}, 物品={}, 数量={}, 操作人={}",
                    request.getDocNo(), fromWarehouseId, request.getWarehouseId(),
                    line.getItemId(), QtyUtils.toContractString(line.getQty()), request.getOperator());
        }
        return result;
    }

    /**
     * 校验调拨行:序列号数量匹配、库位必填(源/目的各自按开关)。
     *
     * @param fromWarehouse 源仓
     * @param toWarehouse   目的仓
     * @param line          调拨行
     */
    private void validateTransferLine(WarehouseDO fromWarehouse, WarehouseDO toWarehouse, StockLine line) {
        long fromLocationId = line.getLocationId() == null ? NO_ID : line.getLocationId();
        long toLocationId = line.getToLocationId() == null ? NO_ID : line.getToLocationId();
        if (Boolean.TRUE.equals(fromWarehouse.getEnableSerial())) {
            if (line.getSerialNos() == null || line.getSerialNos().isEmpty()) {
                throw new BizException("源仓启用序列号,调拨行必须填写序列号");
            }
            if (new BigDecimal(line.getSerialNos().size()).compareTo(line.getQty()) != 0) {
                throw new BizException("源仓启用序列号,序列号数量(" + line.getSerialNos().size()
                        + ")必须等于调拨数量(" + QtyUtils.toContractString(line.getQty()) + ")");
            }
        }
        if (Boolean.TRUE.equals(fromWarehouse.getEnableLocation()) && fromLocationId == NO_ID) {
            throw new BizException("源仓启用库位,调拨行必须填写源库位");
        }
        if (Boolean.TRUE.equals(toWarehouse.getEnableLocation()) && toLocationId == NO_ID) {
            throw new BizException("目的仓启用库位,调拨行必须填写目的库位");
        }
    }

    /**
     * 序列号逐号条件出库(状态 in_stock + 仓库匹配),任一失败整单回滚。
     *
     * @param warehouse   仓库配置
     * @param line        出库行
     * @param warehouseId 仓库 ID
     */
    private void markSerialsOutbound(WarehouseDO warehouse, StockLine line, long warehouseId) {
        if (Boolean.TRUE.equals(warehouse.getEnableSerial())
                && line.getSerialNos() != null && !line.getSerialNos().isEmpty()) {
            for (String sn : line.getSerialNos()) {
                int marked = serialMapper.markOutbound(line.getItemId(), sn, warehouseId);
                if (marked == 0) {
                    throw new BizException("序列号 " + sn + " 不存在或已出库,整单回滚");
                }
            }
        }
    }

    /**
     * 预占候选行 FEFO 排序:保质期升序(NULL 排最后,无批次行同列最后),同值按行 id。
     * 启用保质期的仓库跳过已过期批次行。
     *
     * @param warehouse  仓库配置
     * @param candidates 候选余额行(原地排序/过滤)
     */
    private void sortPreAllocCandidates(WarehouseDO warehouse, List<StockDO> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        List<Long> batchIds = candidates.stream().map(StockDO::getBatchId)
                .filter(b -> b != NO_ID).distinct().toList();
        Map<Long, LocalDate> expiryMap = loadBatchMap(batchIds);
        LocalDate today = LocalDate.now();
        candidates.removeIf(row -> Boolean.TRUE.equals(warehouse.getEnableExpiry())
                && row.getBatchId() != NO_ID
                && expiryMap.get(row.getBatchId()) != null
                && expiryMap.get(row.getBatchId()).isBefore(today));
        candidates.sort(Comparator
                .comparing((StockDO s) -> expiryMap.get(s.getBatchId()) == null)
                .thenComparing(s -> expiryMap.get(s.getBatchId()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockDO::getId));
    }

    /**
     * 可用量口径(全局统一):available = quantity - preAllocatedQty。
     *
     * @param row 余额行
     * @return 可用量
     */
    private BigDecimal availableOf(StockDO row) {
        BigDecimal quantity = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
        BigDecimal pre = row.getPreAllocatedQty() == null ? BigDecimal.ZERO : row.getPreAllocatedQty();
        return quantity.subtract(pre);
    }

    /** 销售发货扣减片段:批次/库位/实扣数量/扣后余额行。 */
    private record SalesDeductPiece(long batchId, long locationId, BigDecimal qty, StockDO after) {
    }

    /**
     * 校验单行数据是否满足仓库配置(默认要求库位,委托四参重载)。
     *
     * @param warehouse 仓库配置
     * @param line      当前行
     * @param direction 方向:inbound / outbound
     */
    private void validateByWarehouseConfig(WarehouseDO warehouse, StockLine line, String direction) {
        validateByWarehouseConfig(warehouse, line, direction, true);
    }

    /**
     * 校验单行数据是否满足仓库配置(4 开关驱动必填校验,规则与 TS 版一致)。
     *
     * @param warehouse      仓库配置
     * @param line           当前行
     * @param direction      方向:inbound / outbound
     * @param requireLocation 是否要求库位(销售发货时库位取自实际预占行,传 false)
     */
    private void validateByWarehouseConfig(WarehouseDO warehouse, StockLine line, String direction,
            boolean requireLocation) {
        boolean inboundDirection = ErrorCode.DIRECTION_INBOUND.equals(direction);
        if (inboundDirection) {
            if (Boolean.TRUE.equals(warehouse.getEnableExpiry())
                    && !hasText(line.getBatchNo()) && line.getExpiryDate() == null) {
                throw new BizException("启用保质期的仓库,入库行必须指定批次或保质期");
            }
            if (Boolean.TRUE.equals(warehouse.getEnableSerial())) {
                if (line.getSerialNos() == null || line.getSerialNos().isEmpty()) {
                    throw new BizException("启用序列号的仓库,入库行必须填写序列号");
                }
                if (new BigDecimal(line.getSerialNos().size()).compareTo(line.getQty()) != 0) {
                    throw new BizException("启用序列号的仓库,序列号数量("
                            + line.getSerialNos().size()
                            + ")必须等于入库数量(" + QtyUtils.toContractString(line.getQty()) + ")");
                }
                if (line.getQty().remainder(ONE).signum() != 0) {
                    throw new BizException("启用序列号的仓库,入库数量必须为整数");
                }
            }
        } else {
            if (Boolean.TRUE.equals(warehouse.getEnableSerial())
                    && (line.getSerialNos() == null || line.getSerialNos().isEmpty())) {
                throw new BizException("启用序列号的仓库,出库行必须填写序列号");
            }
        }
        if (requireLocation && Boolean.TRUE.equals(warehouse.getEnableLocation())
                && (line.getLocationId() == null || line.getLocationId() == NO_ID)) {
            throw new BizException("启用库位的仓库,"
                    + (inboundDirection ? "入库" : "出库") + "行必须填写库位");
        }
    }

    /**
     * 加载仓库,不存在则抛业务异常。
     *
     * @param warehouseId 仓库 ID
     * @return 仓库实体
     */
    private WarehouseDO requireWarehouse(long warehouseId) {
        WarehouseDO warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BizException("仓库不存在: id=" + warehouseId);
        }
        return warehouse;
    }

    /**
     * 按 (itemId, batchNo) 唯一键查批次。
     *
     * @param itemId  物品 ID
     * @param batchNo 批次号
     * @return 批次实体或 null
     */
    private BatchDO findBatch(long itemId, String batchNo) {
        return batchMapper.selectOne(new LambdaQueryWrapper<BatchDO>()
                .eq(BatchDO::getItemId, itemId)
                .eq(BatchDO::getBatchNo, batchNo));
    }

    /**
     * 按余额行唯一键 (warehouse, item, batch, location) 查库存行。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID
     * @param locationId  库位 ID
     * @return 库存行或 null
     */
    private StockDO findStock(long warehouseId, long itemId, long batchId, long locationId) {
        return stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .eq(StockDO::getItemId, itemId)
                .eq(StockDO::getBatchId, batchId)
                .eq(StockDO::getLocationId, locationId));
    }

    /**
     * 写流水(只插不改)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID
     * @param locationId  库位 ID
     * @param changeQty   变动数量(入库正/出库负)
     * @param afterQty    变动后余额
     * @param bizCode     业务类型
     * @param request     操作请求(取单号与操作人)
     */
    private void insertTransaction(long warehouseId, long itemId, long batchId, long locationId,
            BigDecimal changeQty, BigDecimal afterQty, String bizCode, StockOpRequest request) {
        insertTransaction(warehouseId, itemId, batchId, locationId, changeQty, afterQty, bizCode,
                request.getDocNo(), request.getOperator());
    }

    /**
     * 写库存流水(指定单号/操作人,供无单据上下文的库存操作使用)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID
     * @param locationId  库位 ID
     * @param changeQty   变动数量
     * @param afterQty    变动后余额
     * @param bizCode     业务类型
     * @param docNo       单号
     * @param operator    操作人
     */
    private void insertTransaction(long warehouseId, long itemId, long batchId, long locationId,
            BigDecimal changeQty, BigDecimal afterQty, String bizCode, String docNo, String operator) {
        StockTransactionDO tx = new StockTransactionDO();
        tx.setWarehouseId(warehouseId);
        tx.setItemId(itemId);
        tx.setBatchId(batchId);
        tx.setLocationId(locationId);
        tx.setChangeQty(changeQty);
        tx.setAfterQty(afterQty);
        tx.setBizCode(bizCode);
        tx.setDocNo(docNo);
        tx.setOperator(operator);
        // 显式用插入时刻时间:PG 默认 CURRENT_TIMESTAMP 取事务开始时刻,并发下与流水顺序不一致
        tx.setCreatedAt(LocalDateTime.now());
        stockTransactionMapper.insert(tx);
    }

    /**
     * 流水业务类型解析:请求指定则用指定值(调整单等),否则用方向默认值。
     *
     * @param request  操作请求
     * @param defaultCode 方向默认值
     * @return 流水业务类型
     */
    private String resolveBizCode(StockOpRequest request, String defaultCode) {
        return request.getBizCode() == null || request.getBizCode().isEmpty()
                ? defaultCode : request.getBizCode();
    }

    /**
     * 字符串非空白判定。
     *
     * @param s 待判定字符串
     * @return true 表示有内容
     */
    private boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }
}
