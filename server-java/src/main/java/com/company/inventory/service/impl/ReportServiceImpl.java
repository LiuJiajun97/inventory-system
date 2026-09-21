package com.company.inventory.service.impl;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.util.ExcelSupport;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.mapper.report.ReportMapper;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.report.PurchaseReconQuery;
import com.company.inventory.model.query.report.ReportCriteria;
import com.company.inventory.model.query.report.SalesReconQuery;
import com.company.inventory.model.query.report.StockAgeingQuery;
import com.company.inventory.model.query.report.StockMonthlyQuery;
import com.company.inventory.model.vo.excel.PurchaseReconExportRow;
import com.company.inventory.model.vo.excel.SalesReconExportRow;
import com.company.inventory.model.vo.excel.StockAgeingExportRow;
import com.company.inventory.model.vo.excel.StockMonthlyExportRow;
import com.company.inventory.model.vo.report.AgeingRow;
import com.company.inventory.model.vo.report.MonthlyAggRow;
import com.company.inventory.model.vo.report.MonthlyWarehouseDetailVO;
import com.company.inventory.model.vo.report.MonthlyWhRow;
import com.company.inventory.model.vo.report.PurchaseReconVO;
import com.company.inventory.model.vo.report.ReconAggRow;
import com.company.inventory.model.vo.report.ReconDocDetailVO;
import com.company.inventory.model.vo.report.ReconDocRow;
import com.company.inventory.model.vo.report.SalesReconVO;
import com.company.inventory.model.vo.report.StockAgeingVO;
import com.company.inventory.model.vo.report.StockMonthlyVO;
import com.company.inventory.service.ReportService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报表中心服务实现:4 个报表全部为只读 SQL 聚合(ReportMapper.xml)+ 内存轻量整形
 * (名称映射/区间文字/净量计算),不含任何 INSERT/UPDATE/DELETE。
 *
 * <p>数据权限:月报/库龄与 7 列表同口径(admin 豁免、未授权查空、授权仓过滤);
 * 对账按单据所在仓过滤(采购单经关联入库单仓库,销售/退货单按自有仓库)。
 * 时间口径东八区、日期 yyyy-MM-dd(接口参数经 DateRangeSupport 解析)。</p>
 *
 * @author inventory
 */
@Service
public class ReportServiceImpl implements ReportService {

    /** 日期格式(生产日期/单据日期导出)。 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 库龄区间一上限天数(0-30)。 */
    private static final int AGE_BUCKET_1_MAX_DAYS = 30;

    /** 库龄区间二上限天数(31-90)。 */
    private static final int AGE_BUCKET_2_MAX_DAYS = 90;

    /** 库龄区间三上限天数(91-180)。 */
    private static final int AGE_BUCKET_3_MAX_DAYS = 180;

    /** 库龄区间一文字。 */
    private static final String AGE_BUCKET_1 = "0-30";

    /** 库龄区间二文字。 */
    private static final String AGE_BUCKET_2 = "31-90";

    /** 库龄区间三文字。 */
    private static final String AGE_BUCKET_3 = "91-180";

    /** 库龄区间四文字。 */
    private static final String AGE_BUCKET_4 = ">180";

    /** 库龄未知文字(无生产时间)。 */
    private static final String AGE_BUCKET_UNKNOWN = "未知";

    /** 呆滞标记文字。 */
    private static final String STAGNANT_LABEL = "呆滞";

    /** 无批次行的 batch_id 约定值。 */
    private static final long NO_BATCH_ID = 0L;

    /** 单据类型中文名映射(对账行展开)。 */
    private static final Map<String, String> DOC_TYPE_NAMES = Map.of(
            "purchase", "采购订单",
            "purchase_return", "采购退货",
            "sales", "销售订单",
            "sales_return", "销售退货");

    /** 报表 Mapper(只读聚合 SQL)。 */
    private final ReportMapper reportMapper;

    /** 物品 Mapper(名称映射/关键字解析)。 */
    private final ItemMapper itemMapper;

    /** 仓库 Mapper(名称映射)。 */
    private final WarehouseMapper warehouseMapper;

    /** 供应商 Mapper(对账名称映射)。 */
    private final SupplierMapper supplierMapper;

    /** 客户 Mapper(对账名称映射)。 */
    private final CustomerMapper customerMapper;

    /**
     * 构造服务。
     *
     * @param reportMapper     报表 Mapper
     * @param itemMapper       物品 Mapper
     * @param warehouseMapper  仓库 Mapper
     * @param supplierMapper   供应商 Mapper
     * @param customerMapper   客户 Mapper
     */
    public ReportServiceImpl(ReportMapper reportMapper, ItemMapper itemMapper,
            WarehouseMapper warehouseMapper, SupplierMapper supplierMapper, CustomerMapper customerMapper) {
        this.reportMapper = reportMapper;
        this.itemMapper = itemMapper;
        this.warehouseMapper = warehouseMapper;
        this.supplierMapper = supplierMapper;
        this.customerMapper = customerMapper;
    }

    // ==================== 进销存月报 ====================

    @Override
    public PageResult<StockMonthlyVO> monthlyReport(StockMonthlyQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();

        // 数据权限:未授权用户查空;授权用户只查授权仓(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }
        List<Long> itemIds = resolveItemFilter(query.getItemId(), query.getItemKeyword());
        if (itemIds != null && itemIds.isEmpty()) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }

        ReportCriteria q = new ReportCriteria();
        q.setFromDate(DateRangeSupport.parseDate(query.getFrom(), "开始日期"));
        q.setToDate(DateRangeSupport.parseDate(query.getTo(), "结束日期"));
        q.setWarehouseId(query.getWarehouseId());
        q.setItemIds(itemIds);
        q.setAllowedWarehouseIds(allowed);

        long total = reportMapper.countMonthlyItems(q);
        if (total == 0) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }
        q.setLimit(pageSize);
        q.setOffset((page - 1) * pageSize);
        List<MonthlyAggRow> rows = reportMapper.selectMonthlyItems(q);
        List<Long> pageItemIds = rows.stream().map(MonthlyAggRow::itemId).toList();
        Map<Long, ItemDO> itemMap = idMap(
                pageItemIds.isEmpty() ? List.of() : itemMapper.selectByIds(pageItemIds), ItemDO::getId);

        // 行展开:当前页物品各仓期末明细
        List<MonthlyWhRow> whRows = reportMapper.selectMonthlyWarehouseClosing(q, pageItemIds);
        Map<Long, String> whNameMap = warehouseNameMap(
                whRows.stream().map(MonthlyWhRow::warehouseId).collect(Collectors.toSet()));
        Map<Long, List<MonthlyWarehouseDetailVO>> detailMap = whRows.stream()
                .collect(Collectors.groupingBy(MonthlyWhRow::itemId,
                        Collectors.mapping(w -> new MonthlyWarehouseDetailVO(
                                w.warehouseId(), whNameMap.get(w.warehouseId()), QtyUtils.toContractString(w.qty())),
                                Collectors.toList())));

        List<StockMonthlyVO> vos = rows.stream()
                .map(r -> toMonthlyVO(r, itemMap, detailMap)).toList();
        return PageResult.of(vos, total, page, pageSize);
    }

    /**
     * 月报聚合行 → 出参(名称映射 + 数量/金额字符串化)。
     *
     * @param r         SQL 聚合行
     * @param itemMap   物品映射
     * @param detailMap 各仓期末明细映射(缺省空列表)
     * @return 出参
     */
    private StockMonthlyVO toMonthlyVO(MonthlyAggRow r, Map<Long, ItemDO> itemMap,
            Map<Long, List<MonthlyWarehouseDetailVO>> detailMap) {
        ItemDO item = itemMap.get(r.itemId());
        return new StockMonthlyVO(
                r.itemId(),
                item == null ? null : item.getItemCode(),
                item == null ? null : item.getItemName(),
                item == null ? null : item.getUnit(),
                item == null ? null : item.getSpec(),
                QtyUtils.toContractString(r.openingQty()),
                QtyUtils.toContractString(r.inQty()),
                QtyUtils.toContractString(r.outQty()),
                QtyUtils.toContractString(r.closingQty()),
                moneyStr(r.inAmount()),
                moneyStr(r.outAmount()),
                detailMap.getOrDefault(r.itemId(), List.of()));
    }

    // ==================== 库龄/呆滞 ====================

    @Override
    public PageResult<StockAgeingVO> ageingReport(StockAgeingQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();

        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }
        List<Long> itemIds = resolveItemFilter(query.getItemId(), query.getItemKeyword());
        if (itemIds != null && itemIds.isEmpty()) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }

        ReportCriteria q = new ReportCriteria();
        q.setWarehouseId(query.getWarehouseId());
        q.setItemIds(itemIds);
        q.setAllowedWarehouseIds(allowed);
        q.setAgeFrom(query.getAgeFrom());
        q.setAgeTo(query.getAgeTo());
        q.setStagnantDays(query.getStagnantDays() == null
                ? StockAgeingQuery.DEFAULT_STAGNANT_DAYS : query.getStagnantDays());

        long total = reportMapper.countAgeing(q);
        if (total == 0) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }
        q.setLimit(pageSize);
        q.setOffset((page - 1) * pageSize);
        List<AgeingRow> rows = reportMapper.selectAgeing(q);

        List<Long> rowItemIds = rows.stream().map(AgeingRow::itemId).distinct().toList();
        Map<Long, ItemDO> itemMap = idMap(
                rowItemIds.isEmpty() ? List.of() : itemMapper.selectByIds(rowItemIds), ItemDO::getId);
        Map<Long, String> whNameMap = warehouseNameMap(rows.stream()
                .map(AgeingRow::warehouseId).collect(Collectors.toSet()));

        List<StockAgeingVO> vos = rows.stream().map(r -> {
            ItemDO item = itemMap.get(r.itemId());
            return new StockAgeingVO(
                    r.warehouseId(), whNameMap.get(r.warehouseId()), r.itemId(), r.batchId(),
                    item == null ? null : item.getItemCode(),
                    item == null ? null : item.getItemName(),
                    item == null ? null : item.getUnit(),
                    NO_BATCH_ID == r.batchId() ? null : r.batchNo(),
                    r.prodDate(), r.ageDays(), ageBucket(r.ageDays()),
                    QtyUtils.toContractString(r.qty()), Boolean.TRUE.equals(r.stagnant()));
        }).toList();
        return PageResult.of(vos, total, page, pageSize);
    }

    /**
     * 库龄天数 → 区间文字(0-30/31-90/91-180/>180,无生产时间为"未知")。
     *
     * @param ageDays 库龄天数(可空)
     * @return 区间文字
     */
    private String ageBucket(Integer ageDays) {
        if (ageDays == null) {
            return AGE_BUCKET_UNKNOWN;
        }
        if (ageDays <= AGE_BUCKET_1_MAX_DAYS) {
            return AGE_BUCKET_1;
        }
        if (ageDays <= AGE_BUCKET_2_MAX_DAYS) {
            return AGE_BUCKET_2;
        }
        if (ageDays <= AGE_BUCKET_3_MAX_DAYS) {
            return AGE_BUCKET_3;
        }
        return AGE_BUCKET_4;
    }

    // ==================== 采购对账 ====================

    @Override
    public PageResult<PurchaseReconVO> purchaseRecon(PurchaseReconQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();

        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }

        ReportCriteria q = new ReportCriteria();
        q.setFromDate(DateRangeSupport.parseDate(query.getFrom(), "开始日期"));
        q.setToDate(DateRangeSupport.parseDate(query.getTo(), "结束日期"));
        q.setSupplierId(query.getSupplierId());
        q.setAllowedWarehouseIds(allowed);

        long total = reportMapper.countPurchaseRecon(q);
        if (total == 0) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }
        q.setLimit(pageSize);
        q.setOffset((page - 1) * pageSize);
        List<ReconAggRow> rows = reportMapper.selectPurchaseRecon(q);
        List<Long> partyIds = rows.stream().map(ReconAggRow::partyId).toList();
        Map<Long, SupplierDO> supplierMap = idMap(
                partyIds.isEmpty() ? List.of() : supplierMapper.selectByIds(partyIds), SupplierDO::getId);

        // 行展开:当前页供应商期间内采购单+退货单明细
        List<ReconDocRow> docRows = reportMapper.selectPurchaseReconDocs(q, partyIds);
        Map<Long, List<ReconDocDetailVO>> detailMap = reconDetailMap(docRows);

        List<PurchaseReconVO> vos = rows.stream().map(r -> {
            SupplierDO sup = supplierMap.get(r.partyId());
            BigDecimal netQty = r.orderQty().subtract(r.returnQty());
            BigDecimal netAmount = r.orderAmount().subtract(r.returnAmount());
            return new PurchaseReconVO(
                    r.partyId(),
                    sup == null ? null : sup.getSupplierCode(),
                    sup == null ? null : sup.getSupplierName(),
                    r.orderCount(),
                    QtyUtils.toContractString(r.orderQty()),
                    moneyStr(r.orderAmount()),
                    QtyUtils.toContractString(r.returnQty()),
                    moneyStr(r.returnAmount()),
                    QtyUtils.toContractString(netQty),
                    moneyStr(netAmount),
                    detailMap.getOrDefault(r.partyId(), List.of()));
        }).toList();
        return PageResult.of(vos, total, page, pageSize);
    }

    // ==================== 销售对账 ====================

    @Override
    public PageResult<SalesReconVO> salesRecon(SalesReconQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();

        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }

        ReportCriteria q = new ReportCriteria();
        q.setFromDate(DateRangeSupport.parseDate(query.getFrom(), "开始日期"));
        q.setToDate(DateRangeSupport.parseDate(query.getTo(), "结束日期"));
        q.setCustomerId(query.getCustomerId());
        q.setAllowedWarehouseIds(allowed);

        long total = reportMapper.countSalesRecon(q);
        if (total == 0) {
            return PageResult.of(List.of(), 0L, page, pageSize);
        }
        q.setLimit(pageSize);
        q.setOffset((page - 1) * pageSize);
        List<ReconAggRow> rows = reportMapper.selectSalesRecon(q);
        List<Long> partyIds = rows.stream().map(ReconAggRow::partyId).toList();
        Map<Long, CustomerDO> customerMap = idMap(
                partyIds.isEmpty() ? List.of() : customerMapper.selectByIds(partyIds), CustomerDO::getId);

        // 行展开:当前页客户期间内销售单+退货单明细
        List<ReconDocRow> docRows = reportMapper.selectSalesReconDocs(q, partyIds);
        Map<Long, List<ReconDocDetailVO>> detailMap = reconDetailMap(docRows);

        List<SalesReconVO> vos = rows.stream().map(r -> {
            CustomerDO cus = customerMap.get(r.partyId());
            BigDecimal netQty = r.orderQty().subtract(r.returnQty());
            BigDecimal netAmount = r.orderAmount().subtract(r.returnAmount());
            return new SalesReconVO(
                    r.partyId(),
                    cus == null ? null : cus.getCustomerCode(),
                    cus == null ? null : cus.getCustomerName(),
                    r.orderCount(),
                    QtyUtils.toContractString(r.orderQty()),
                    moneyStr(r.orderAmount()),
                    QtyUtils.toContractString(r.returnQty()),
                    moneyStr(r.returnAmount()),
                    QtyUtils.toContractString(netQty),
                    moneyStr(netAmount),
                    detailMap.getOrDefault(r.partyId(), List.of()));
        }).toList();
        return PageResult.of(vos, total, page, pageSize);
    }

    /**
     * 对账单据明细行 → 出参映射(按供应商/客户分组,组内保持日期排序)。
     *
     * @param docRows SQL 明细行
     * @return partyId → 明细列表
     */
    private Map<Long, List<ReconDocDetailVO>> reconDetailMap(List<ReconDocRow> docRows) {
        return docRows.stream().collect(Collectors.groupingBy(ReconDocRow::partyId,
                Collectors.mapping(r -> new ReconDocDetailVO(
                        r.docNo(), r.docDate(), r.docType(),
                        DOC_TYPE_NAMES.getOrDefault(r.docType(), r.docType()),
                        moneyStr(r.amount())),
                        Collectors.toList())));
    }

    // ==================== 导出(4 个,与列表读权限一致) ====================

    @Override
    public void exportMonthlyReport(StockMonthlyQuery query, HttpServletResponse resp) {
        List<StockMonthlyVO> vos = monthlyReport(exportQuery(query)).rows();
        List<StockMonthlyExportRow> rows = vos.stream().map(v -> {
            StockMonthlyExportRow row = new StockMonthlyExportRow();
            row.setItemCode(v.itemCode());
            row.setItemName(v.itemName());
            row.setUnit(v.unit());
            row.setSpec(v.spec());
            row.setOpeningQty(v.openingQty());
            row.setInQty(v.inQty());
            row.setOutQty(v.outQty());
            row.setClosingQty(v.closingQty());
            row.setInAmount(v.inAmount() == null ? "" : v.inAmount());
            row.setOutAmount(v.outAmount() == null ? "" : v.outAmount());
            return row;
        }).toList();
        ExcelSupport.writeXlsx(resp, "stock-monthly-report", "进销存月报", "进销存月报",
                StockMonthlyExportRow.class, rows);
    }

    @Override
    public void exportAgeingReport(StockAgeingQuery query, HttpServletResponse resp) {
        List<StockAgeingVO> vos = ageingReport(exportQuery(query)).rows();
        List<StockAgeingExportRow> rows = vos.stream().map(v -> {
            StockAgeingExportRow row = new StockAgeingExportRow();
            row.setWarehouseName(v.warehouseName());
            row.setItemCode(v.itemCode());
            row.setItemName(v.itemName());
            row.setBatchNo(v.batchNo());
            row.setProductionDate(v.productionDate() == null ? "" : v.productionDate().format(DATE_FMT));
            row.setAgeDays(v.ageDays() == null ? "" : String.valueOf(v.ageDays()));
            row.setAgeBucket(v.ageBucket());
            row.setQuantity(v.quantity());
            row.setStagnant(Boolean.TRUE.equals(v.stagnant()) ? STAGNANT_LABEL : "");
            return row;
        }).toList();
        ExcelSupport.writeXlsx(resp, "stock-ageing-report", "库龄呆滞", "库龄呆滞",
                StockAgeingExportRow.class, rows);
    }

    @Override
    public void exportPurchaseRecon(PurchaseReconQuery query, HttpServletResponse resp) {
        List<PurchaseReconVO> vos = purchaseRecon(exportQuery(query)).rows();
        List<PurchaseReconExportRow> rows = vos.stream().map(v -> {
            PurchaseReconExportRow row = new PurchaseReconExportRow();
            row.setSupplierCode(v.supplierCode());
            row.setSupplierName(v.supplierName());
            row.setOrderCount(String.valueOf(v.orderCount()));
            row.setOrderQty(v.orderQty());
            row.setOrderAmount(v.orderAmount() == null ? "" : v.orderAmount());
            row.setReturnQty(v.returnQty());
            row.setReturnAmount(v.returnAmount() == null ? "" : v.returnAmount());
            row.setNetQty(v.netQty());
            row.setNetAmount(v.netAmount() == null ? "" : v.netAmount());
            return row;
        }).toList();
        ExcelSupport.writeXlsx(resp, "purchase-recon-report", "采购对账", "采购对账",
                PurchaseReconExportRow.class, rows);
    }

    @Override
    public void exportSalesRecon(SalesReconQuery query, HttpServletResponse resp) {
        List<SalesReconVO> vos = salesRecon(exportQuery(query)).rows();
        List<SalesReconExportRow> rows = vos.stream().map(v -> {
            SalesReconExportRow row = new SalesReconExportRow();
            row.setCustomerCode(v.customerCode());
            row.setCustomerName(v.customerName());
            row.setOrderCount(String.valueOf(v.orderCount()));
            row.setOrderQty(v.orderQty());
            row.setOrderAmount(v.orderAmount() == null ? "" : v.orderAmount());
            row.setReturnQty(v.returnQty());
            row.setReturnAmount(v.returnAmount() == null ? "" : v.returnAmount());
            row.setNetQty(v.netQty());
            row.setNetAmount(v.netAmount() == null ? "" : v.netAmount());
            return row;
        }).toList();
        ExcelSupport.writeXlsx(resp, "sales-recon-report", "销售对账", "销售对账",
                SalesReconExportRow.class, rows);
    }

    // ==================== 公共辅助 ====================

    /**
     * 导出查询归一化:不分页(单页取满)。
     *
     * @param <T>   查询条件类型
     * @param query 列表查询条件
     * @return 归一化后的查询条件(同一对象)
     */
    private <T extends com.company.inventory.common.page.PageQuery> T exportQuery(T query) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        return query;
    }

    /**
     * 物品过滤解析:itemId 优先(精确),其次关键字(编码/名称模糊)。
     *
     * @param itemId  物品 ID(可空)
     * @param keyword 关键字(可空;两者都空返回 null 表示不过滤)
     * @return 物品 ID 列表(可空)
     */
    private List<Long> resolveItemFilter(Long itemId, String keyword) {
        if (itemId != null) {
            return List.of(itemId);
        }
        return resolveItemIds(keyword);
    }

    /**
     * 物品关键字解析(编码或名称模糊)→ 物品 ID 列表。
     *
     * @param keyword 关键字(可空;空返回 null 表示不过滤)
     * @return 物品 ID 列表(可空;命中为空时返回空列表)
     */
    private List<Long> resolveItemIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String like = keyword.trim();
        List<ItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<ItemDO>()
                .and(w -> w.like(ItemDO::getItemCode, like)
                        .or().like(ItemDO::getItemName, like)));
        return items.stream().map(ItemDO::getId).toList();
    }

    /**
     * ID → 实体映射(空集合防护:入参已保证非空集才查库)。
     *
     * @param <T>   实体类型
     * @param list  实体列表
     * @param keyFn 取 ID 函数
     * @return ID 映射
     */
    private <T> Map<Long, T> idMap(List<T> list, Function<T, Long> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, Function.identity(), (a, b) -> a));
    }

    /**
     * 仓库 ID 集合 → 仓库名映射(空集合防护)。
     *
     * @param whIds 仓库 ID 集合(可空)
     * @return 仓库名映射
     */
    private Map<Long, String> warehouseNameMap(Set<Long> whIds) {
        if (whIds == null || whIds.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectByIds(whIds).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, WarehouseDO::getWarehouseName, (a, b) -> a));
    }

    /**
     * 金额 BigDecimal → 展示字符串(统一 2 位小数,无快照为 null)。
     *
     * @param value 金额(可空)
     * @return 字符串或 null
     */
    private String moneyStr(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
