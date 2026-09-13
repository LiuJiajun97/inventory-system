package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.util.ExcelSupport;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.InboundDocItemMapper;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.OpeningStockDocItemMapper;
import com.company.inventory.mapper.OpeningStockDocMapper;
import com.company.inventory.mapper.StockTransactionMapper;
import com.company.inventory.mapper.TransferDocMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.entity.inbound.InboundDocDO;
import com.company.inventory.model.entity.inbound.InboundDocItemDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.opening.OpeningStockDocDO;
import com.company.inventory.model.entity.opening.OpeningStockDocItemDO;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.stock.StockTransactionDO;
import com.company.inventory.model.entity.transfer.TransferDocDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.report.CostReportQuery;
import com.company.inventory.model.vo.excel.CostReportExportRow;
import com.company.inventory.model.vo.report.CostReportRow;
import com.company.inventory.model.vo.report.CostReportVO;
import com.company.inventory.service.CostReportService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存成本(移动均价)报表实现:零建表,按 stock_transaction 实时回放计算,纯只读。
 *
 * <p>回放规则(成本单元 = 仓库 + 物品 + 批次,批次 0/null 也独立成单元):
 * 按 created_at(同时间戳按 id)升序回放;
 * opening/inbound → 金额 += 数量×单据行单价,均价 = 金额/数量(找不到行单价的入库流水
 * 金额按 0 计入,不静默跳过,均价随后校准);
 * outbound/transfer_out/adjust_out(盘亏)→ 金额 -= 数量×当前均价(均价不变);
 * transfer_in → 金额 += 数量×源仓当前均价(成本随货走,源/目的仓单元同步,同单号先出后入);
 * adjust_in(盘盈)→ 金额 += 数量×当前均价(均价不变);
 * pre_alloc 等辅助流水不影响金额;
 * 数量一律以流水 afterQty 为权威(同库位最后一条覆盖,单元数量 = 各库位余额之和)。</p>
 *
 * <p>数据权限与报表中心一致(admin 豁免、未授权查空、授权仓过滤输出行);
 * 回放本身不过滤仓库(调拨源仓必须参与回放,成本才正确)。</p>
 *
 * @author inventory
 */
@Service
public class CostReportServiceImpl implements CostReportService {

    /** 均价内部精度(scale 8 HALF_UP)。 */
    private static final int AVG_PRICE_SCALE = 8;

    /** 均价展示精度(2.625 这类均价 2 位会失真,展示 4 位)。 */
    private static final int DISPLAY_PRICE_SCALE = 4;

    /** 金额展示精度。 */
    private static final int AMOUNT_DISPLAY_SCALE = 2;

    /** 无批次/无库位占位值。 */
    private static final long NO_ID = 0L;

    /** 期初流水业务类型(防御:现行链路期初经入库单过账,bizCode 实为 inbound)。 */
    private static final String BIZ_CODE_OPENING = "opening";

    /** 回放截止日默认时点:23。 */
    private static final int END_OF_DAY_HOUR = 23;
    /** 回放截止日默认时点:59。 */
    private static final int END_OF_DAY_MINUTE = 59;
    /** 回放截止日默认时点:59。 */
    private static final int END_OF_DAY_SECOND = 59;

    /** 流水 Mapper(回放数据源)。 */
    private final StockTransactionMapper stockTransactionMapper;
    /** 物品 Mapper(名称映射)。 */
    private final ItemMapper itemMapper;
    /** 仓库 Mapper(名称映射)。 */
    private final WarehouseMapper warehouseMapper;
    /** 批次 Mapper(批次号映射)。 */
    private final BatchMapper batchMapper;
    /** 入库单 Mapper(行单价关联,经回放上下文访问)。 */
    private final InboundDocMapper inboundDocMapper;
    /** 入库单行 Mapper(行单价,经回放上下文访问)。 */
    private final InboundDocItemMapper inboundDocItemMapper;
    /** 期初单 Mapper(docNo 兜底关联,经回放上下文访问)。 */
    private final OpeningStockDocMapper openingStockDocMapper;
    /** 期初单行 Mapper(行单价兜底,经回放上下文访问)。 */
    private final OpeningStockDocItemMapper openingStockDocItemMapper;
    /** 调拨单 Mapper(调拨入取源仓,经回放上下文访问)。 */
    private final TransferDocMapper transferDocMapper;

    /**
     * 构造服务。
     *
     * @param stockTransactionMapper    流水 Mapper
     * @param itemMapper                物品 Mapper
     * @param warehouseMapper           仓库 Mapper
     * @param batchMapper               批次 Mapper
     * @param inboundDocMapper          入库单 Mapper
     * @param inboundDocItemMapper      入库单行 Mapper
     * @param openingStockDocMapper     期初单 Mapper
     * @param openingStockDocItemMapper 期初单行 Mapper
     * @param transferDocMapper         调拨单 Mapper
     */
    public CostReportServiceImpl(StockTransactionMapper stockTransactionMapper,
            ItemMapper itemMapper, WarehouseMapper warehouseMapper, BatchMapper batchMapper,
            InboundDocMapper inboundDocMapper, InboundDocItemMapper inboundDocItemMapper,
            OpeningStockDocMapper openingStockDocMapper,
            OpeningStockDocItemMapper openingStockDocItemMapper, TransferDocMapper transferDocMapper) {
        this.stockTransactionMapper = stockTransactionMapper;
        this.itemMapper = itemMapper;
        this.warehouseMapper = warehouseMapper;
        this.batchMapper = batchMapper;
        this.inboundDocMapper = inboundDocMapper;
        this.inboundDocItemMapper = inboundDocItemMapper;
        this.openingStockDocMapper = openingStockDocMapper;
        this.openingStockDocItemMapper = openingStockDocItemMapper;
        this.transferDocMapper = transferDocMapper;
    }

    @Override
    public CostReportVO costReport(CostReportQuery query) {
        // 数据权限:未授权任何仓库 → 查空(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return new CostReportVO(List.of(), QtyUtils.toContractString(BigDecimal.ZERO),
                    moneyStr(BigDecimal.ZERO));
        }
        LocalDateTime cutoff = resolveCutoff(query.getDate());

        // 回放流水:按物品/截止日过滤;不按仓过滤(调拨源仓必须参与回放)
        LambdaQueryWrapper<StockTransactionDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getItemId() != null) {
            wrapper.eq(StockTransactionDO::getItemId, query.getItemId());
        }
        if (cutoff != null) {
            wrapper.le(StockTransactionDO::getCreatedAt, cutoff);
        }
        wrapper.orderByAsc(StockTransactionDO::getCreatedAt).orderByAsc(StockTransactionDO::getId);
        List<StockTransactionDO> txs = stockTransactionMapper.selectList(wrapper);
        if (txs.isEmpty()) {
            return new CostReportVO(List.of(), QtyUtils.toContractString(BigDecimal.ZERO),
                    moneyStr(BigDecimal.ZERO));
        }

        // 回放(单据/行单价缓存按 docNo 懒加载,防循环查库)
        ReplayContext ctx = new ReplayContext();
        Map<UnitKey, UnitState> units = new LinkedHashMap<>();
        for (StockTransactionDO tx : txs) {
            replay(tx, units, ctx);
        }

        // 组装行:按查询仓/授权仓过滤输出,仓 → 物品 → 批次排序
        List<UnitKey> keys = units.keySet().stream()
                .filter(k -> query.getWarehouseId() == null || k.warehouseId().equals(query.getWarehouseId()))
                .filter(k -> allowed == null || allowed.contains(k.warehouseId()))
                .sorted(Comparator.comparing(UnitKey::warehouseId)
                        .thenComparing(UnitKey::itemId)
                        .thenComparing(UnitKey::batchId))
                .toList();
        Map<Long, ItemDO> itemMap = entityMap(keys.stream().map(UnitKey::itemId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toSet()),
                itemMapper::selectByIds, ItemDO::getId);
        Map<Long, String> whNameMap = warehouseNameMap(keys.stream().map(UnitKey::warehouseId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, BatchDO> batchMap = batchMap(keys.stream().map(UnitKey::batchId)
                .filter(b -> b != null && b != NO_ID).distinct().collect(Collectors.toSet()));

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<CostReportRow> rows = new ArrayList<>();
        for (UnitKey key : keys) {
            UnitState st = units.get(key);
            ItemDO item = itemMap.get(key.itemId());
            BatchDO batch = NO_ID == key.batchId() ? null : batchMap.get(key.batchId());
            rows.add(new CostReportRow(key.warehouseId(), whNameMap.get(key.warehouseId()),
                    key.itemId(), item == null ? null : item.getItemCode(),
                    item == null ? null : item.getItemName(), item == null ? null : item.getUnit(),
                    key.batchId(), batch == null ? null : batch.getBatchNo(),
                    QtyUtils.toContractString(st.qty), priceStr(st.avg), moneyStr(st.amount)));
            totalQty = totalQty.add(st.qty);
            totalAmount = totalAmount.add(st.amount);
        }
        return new CostReportVO(rows, QtyUtils.toContractString(totalQty), moneyStr(totalAmount));
    }

    @Override
    public void exportCostReport(CostReportQuery query, HttpServletResponse resp) {
        List<CostReportRow> rows = costReport(query).rows();
        List<CostReportExportRow> excelRows = rows.stream().map(v -> {
            CostReportExportRow row = new CostReportExportRow();
            row.setWarehouseName(v.warehouseName());
            row.setItemCode(v.itemCode());
            row.setItemName(v.itemName());
            row.setUnit(v.unit());
            row.setBatchNo(v.batchNo());
            row.setQuantity(v.quantity());
            row.setAvgPrice(v.avgPrice());
            row.setAmount(v.amount());
            return row;
        }).toList();
        ExcelSupport.writeXlsx(resp, "stock-cost-report", "库存成本", "库存成本",
                CostReportExportRow.class, excelRows);
    }

    /**
     * 回放截止时点解析:不传默认当前(不截断),传则取该日 23:59:59(东八区)。
     *
     * @param date 回放截止日(yyyy-MM-dd,可空)
     * @return 截止时点(可空 = 不截断)
     */
    private LocalDateTime resolveCutoff(String date) {
        LocalDate day = DateRangeSupport.parseDate(date, "回放截止日");
        return day == null ? null
                : day.atTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE, END_OF_DAY_SECOND);
    }

    /**
     * 回放单条流水:按业务类型更新金额,数量以 afterQty 为权威按库位校准。
     *
     * @param tx    流水
     * @param units 单元状态映射(会被修改)
     * @param ctx   回放上下文(单据/行单价缓存)
     */
    private void replay(StockTransactionDO tx, Map<UnitKey, UnitState> units, ReplayContext ctx) {
        long batchId = tx.getBatchId() == null ? NO_ID : tx.getBatchId();
        UnitKey key = new UnitKey(tx.getWarehouseId(), tx.getItemId(), batchId);
        UnitState st = units.computeIfAbsent(key, UnitKey::newState);
        BigDecimal qty = tx.getChangeQty();
        String biz = tx.getBizCode();
        if (ErrorCode.BIZ_CODE_INBOUND.equals(biz) || BIZ_CODE_OPENING.equals(biz)) {
            BigDecimal price = ctx.lookupWeightedPrice(tx.getDocNo(), tx.getItemId(), batchId);
            // 找不到行单价的入库流水:金额按 0 计入(不静默跳过,均价随后按金额/数量校准)
            st.amount = st.amount.add(price == null ? BigDecimal.ZERO : qty.multiply(price));
            // 加权更新:均价 = 金额/数量(用本笔后的单元数量,防扣至 0 后均价丢失)
            BigDecimal newQty = st.qty.add(qty);
            st.avg = newQty.signum() > 0
                    ? st.amount.divide(newQty, AVG_PRICE_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        } else if (ErrorCode.BIZ_CODE_OUTBOUND.equals(biz) || ErrorCode.BIZ_CODE_TRANSFER_OUT.equals(biz)
                || ErrorCode.BIZ_CODE_ADJUST_OUT.equals(biz)) {
            // 消耗:均价不变(即使扣至 0 也保留最近均价,供调拨入按原单结转)
            if (st.qty.signum() > 0) {
                st.amount = st.amount.subtract(qty.abs().multiply(st.avg));
            }
        } else if (ErrorCode.BIZ_CODE_TRANSFER_IN.equals(biz)) {
            // 成本随货走:按源仓当时均价入账,本单元均价重算为 金额/数量
            st.amount = st.amount.add(qty.multiply(ctx.transferInPrice(tx, key, units)));
            BigDecimal newQty = st.qty.add(qty);
            st.avg = newQty.signum() > 0
                    ? st.amount.divide(newQty, AVG_PRICE_SCALE, RoundingMode.HALF_UP)
                    : st.avg;
        } else if (ErrorCode.BIZ_CODE_ADJUST_IN.equals(biz)) {
            // 盘盈:按当前均价入账,均价不变
            if (st.qty.signum() > 0) {
                st.amount = st.amount.add(qty.multiply(st.avg));
            }
        }
        // pre_alloc / release_pre_alloc 等辅助流水:不影响金额
        // 数量以 afterQty 为权威:同库位最后一条覆盖,单元数量 = 各库位余额之和
        long locationId = tx.getLocationId() == null ? NO_ID : tx.getLocationId();
        st.locationQty.put(locationId, tx.getAfterQty());
        BigDecimal unitQty = BigDecimal.ZERO;
        for (BigDecimal locQty : st.locationQty.values()) {
            unitQty = unitQty.add(locQty);
        }
        st.qty = unitQty;
    }

    /**
     * 均价展示字符串(4 位小数,2.625 这类均价 2 位会失真)。
     *
     * @param value 均价
     * @return 字符串
     */
    private String priceStr(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.toPlainString()
                : value.setScale(DISPLAY_PRICE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 金额展示字符串(2 位小数)。
     *
     * @param value 金额
     * @return 字符串
     */
    private String moneyStr(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.toPlainString()
                : value.setScale(AMOUNT_DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * ID 集合 → 实体映射(空集合防护:空集合直接返回空映射,不查库)。
     *
     * @param <T>    实体类型
     * @param ids    ID 集合
     * @param loader 批量加载函数
     * @param keyFn  取 ID 函数
     * @return ID 映射
     */
    private <T> Map<Long, T> entityMap(Set<Long> ids,
            Function<List<Long>, List<T>> loader, Function<T, Long> keyFn) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loader.apply(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(keyFn, Function.identity(), (a, b) -> a));
    }

    /**
     * 仓库 ID 集合 → 仓库名映射(空集合防护)。
     *
     * @param whIds 仓库 ID 集合
     * @return 仓库名映射
     */
    private Map<Long, String> warehouseNameMap(Set<Long> whIds) {
        if (whIds.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectByIds(whIds).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, WarehouseDO::getWarehouseName, (a, b) -> a));
    }

    /**
     * 批次 ID 集合 → 批次实体映射(空集合防护)。
     *
     * @param batchIds 批次 ID 集合
     * @return 批次映射
     */
    private Map<Long, BatchDO> batchMap(Set<Long> batchIds) {
        if (batchIds.isEmpty()) {
            return Map.of();
        }
        return batchMapper.selectByIds(batchIds).stream()
                .collect(Collectors.toMap(BatchDO::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 成本单元键(仓库 + 物品 + 批次)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID(0 无批次)
     * @author inventory
     */
    private record UnitKey(Long warehouseId, Long itemId, long batchId) {

        /**
         * 新建单元状态。
         *
         * @return 单元状态
         */
        UnitState newState() {
            return new UnitState();
        }
    }

    /**
     * 成本单元回放状态(数量/金额/均价 + 各库位 afterQty 权威余额)。
     *
     * @author inventory
     */
    private static final class UnitState {

        /** 当前数量(各库位 afterQty 之和)。 */
        private BigDecimal qty = BigDecimal.ZERO;
        /** 当前成本金额。 */
        private BigDecimal amount = BigDecimal.ZERO;
        /** 当前移动均价(scale 8)。 */
        private BigDecimal avg = BigDecimal.ZERO;
        /** 各库位最后 afterQty(权威余额,同库位后写覆盖)。 */
        private final Map<Long, BigDecimal> locationQty = new HashMap<>();
    }

    /**
     * 回放上下文:入库单/期初单/调拨单的 docNo 懒加载缓存(单次请求内有效,防循环查库)。
     *
     * @author inventory
     */
    private final class ReplayContext {

        /** 入库单缓存(docNo → 单)。 */
        private final Map<String, InboundDocDO> inboundDocs = new HashMap<>();
        /** 入库单行缓存(docId → 行)。 */
        private final Map<Long, List<InboundDocItemDO>> inboundItems = new HashMap<>();
        /** 期初单缓存(docNo → 单)。 */
        private final Map<String, OpeningStockDocDO> openingDocs = new HashMap<>();
        /** 期初单行缓存(docId → 行)。 */
        private final Map<Long, List<OpeningStockDocItemDO>> openingItems = new HashMap<>();
        /** 调拨单缓存(docNo → 单)。 */
        private final Map<String, TransferDocDO> transferDocs = new HashMap<>();
        /** 批次号缓存(batchId → 批次号)。 */
        private final Map<Long, String> batchNoCache = new HashMap<>();

        /**
         * 加权入库行单价查找:按 docNo 先查入库单行,再兜底期初单行,按 物品 + 批次 匹配。
         *
         * @param docNo   流水关联单号(可空)
         * @param itemId  物品 ID
         * @param batchId 批次 ID(0 无批次)
         * @return 行单价(找不到为 null)
         */
        BigDecimal lookupWeightedPrice(String docNo, long itemId, long batchId) {
            if (!StringUtils.hasText(docNo)) {
                return null;
            }
            InboundDocDO inboundDoc = inboundDocs.computeIfAbsent(docNo,
                    no -> firstDoc(inboundDocMapper.selectList(new LambdaQueryWrapper<InboundDocDO>()
                            .eq(InboundDocDO::getDocNo, no))));
            if (inboundDoc != null) {
                List<InboundDocItemDO> items = inboundItems.computeIfAbsent(inboundDoc.getId(),
                        docId -> inboundDocItemMapper.selectList(new LambdaQueryWrapper<InboundDocItemDO>()
                                .eq(InboundDocItemDO::getDocId, docId)));
                for (InboundDocItemDO item : items) {
                    if (item.getItemId() != null && item.getItemId() == itemId
                            && normalize(item.getBatchId()) == batchId) {
                        return item.getUnitPrice();
                    }
                }
                return null;
            }
            OpeningStockDocDO openingDoc = openingDocs.computeIfAbsent(docNo,
                    no -> firstDoc(openingStockDocMapper.selectList(new LambdaQueryWrapper<OpeningStockDocDO>()
                            .eq(OpeningStockDocDO::getDocNo, no))));
            if (openingDoc == null) {
                return null;
            }
            List<OpeningStockDocItemDO> items = openingItems.computeIfAbsent(openingDoc.getId(),
                    docId -> openingStockDocItemMapper.selectList(new LambdaQueryWrapper<OpeningStockDocItemDO>()
                            .eq(OpeningStockDocItemDO::getDocId, docId)));
            String txBatchNo = batchNoOf(batchId);
            for (OpeningStockDocItemDO item : items) {
                if (item.getItemId() != null && item.getItemId() == itemId
                        && txBatchNo.equals(item.getBatchNo() == null ? "" : item.getBatchNo())) {
                    return item.getUnitPrice();
                }
            }
            return null;
        }

        /**
         * 批次 ID → 批次号(0/查不到返回空串,用于期初单行匹配)。
         *
         * @param batchId 批次 ID(0 无批次)
         * @return 批次号或空串
         */
        private String batchNoOf(long batchId) {
            if (NO_ID == batchId) {
                return "";
            }
            return batchNoCache.computeIfAbsent(batchId, id -> {
                BatchDO batch = batchMapper.selectById(id);
                return batch == null ? "" : batch.getBatchNo();
            });
        }

        /**
         * 调拨入结转价:取同单调拨单源仓单元当时的均价(成本随货走);
         * 源仓单元未回放(理论上不存在,同单先出后入)或单据缺失时按 0。
         *
         * @param tx    调拨入流水
         * @param key   目的仓单元键
         * @param units 单元状态映射
         * @return 源仓当时均价(可 0)
         */
        BigDecimal transferInPrice(StockTransactionDO tx, UnitKey key, Map<UnitKey, UnitState> units) {
            if (!StringUtils.hasText(tx.getDocNo())) {
                return BigDecimal.ZERO;
            }
            TransferDocDO doc = transferDocs.computeIfAbsent(tx.getDocNo(),
                    no -> firstDoc(transferDocMapper.selectList(new LambdaQueryWrapper<TransferDocDO>()
                            .eq(TransferDocDO::getDocNo, no))));
            if (doc == null) {
                return BigDecimal.ZERO;
            }
            UnitState src = units.get(new UnitKey(doc.getFromWarehouseId(), key.itemId(), key.batchId()));
            return src == null ? BigDecimal.ZERO : src.avg;
        }

        /**
         * 批次 ID 归一化(null → 0)。
         *
         * @param batchId 原始批次 ID(可空)
         * @return 归一化批次 ID
         */
        private long normalize(Long batchId) {
            return batchId == null ? NO_ID : batchId;
        }

        /**
         * 单条查询结果取值(空列表为 null)。
         *
         * @param <T>  实体类型
         * @param list 查询结果
         * @return 首条或 null
         */
        private <T> T firstDoc(List<T> list) {
            return list == null || list.isEmpty() ? null : list.get(0);
        }
    }
}
