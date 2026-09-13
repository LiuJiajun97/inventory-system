package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ImportExportLabels;
import com.company.inventory.common.util.ExcelSupport;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.CustomerQuery;
import com.company.inventory.model.query.InboundDocQuery;
import com.company.inventory.model.query.ItemQuery;
import com.company.inventory.model.query.OutboundDocQuery;
import com.company.inventory.model.query.PurchaseOrderQuery;
import com.company.inventory.model.query.SalesOrderQuery;
import com.company.inventory.model.query.StockQuery;
import com.company.inventory.model.query.SupplierQuery;
import com.company.inventory.model.vo.customer.CustomerVO;
import com.company.inventory.model.vo.excel.CustomerExportRow;
import com.company.inventory.model.vo.excel.InboundExportRow;
import com.company.inventory.model.vo.excel.ItemExportRow;
import com.company.inventory.model.vo.excel.OutboundExportRow;
import com.company.inventory.model.vo.excel.PurchaseOrderExportRow;
import com.company.inventory.model.vo.excel.SalesOrderExportRow;
import com.company.inventory.model.vo.excel.StockExportRow;
import com.company.inventory.model.vo.excel.SupplierExportRow;
import com.company.inventory.model.vo.inbound.InboundDocVO;
import com.company.inventory.model.vo.item.ItemVO;
import com.company.inventory.model.vo.outbound.OutboundDocVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;
import com.company.inventory.model.vo.sales.SalesOrderVO;
import com.company.inventory.model.vo.stock.StockVO;
import com.company.inventory.model.vo.supplier.SupplierVO;
import com.company.inventory.service.CustomerService;
import com.company.inventory.service.ExportService;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.ItemService;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.StockQueryService;
import com.company.inventory.service.SupplierService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导出服务实现:8 个列表 xlsx 导出。
 *
 * <p>复用既有列表 Query DTO 与 Service 查询(不分页:page=1、pageSize 取
 * {@link ExcelSupport#EXPORT_PAGE_SIZE}),不新写查询 SQL;数量/金额/日期统一
 * String 直出(东八区,JVM 已锁 Asia/Shanghai)。</p>
 *
 * @author inventory
 */
@Service
public class ExportServiceImpl implements ExportService {

    /** 日期格式(单据日期)。 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 时间格式(创建时间)。 */
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 物品服务。 */
    private final ItemService itemService;

    /** 供应商服务。 */
    private final SupplierService supplierService;

    /** 客户服务。 */
    private final CustomerService customerService;

    /** 库存查询服务。 */
    private final StockQueryService stockQueryService;

    /** 入库单服务。 */
    private final InboundService inboundService;

    /** 出库单服务。 */
    private final OutboundService outboundService;

    /** 采购订单服务。 */
    private final PurchaseOrderService purchaseOrderService;

    /** 销售订单服务。 */
    private final SalesOrderService salesOrderService;

    /** 仓库 Mapper(销售订单导出仓库名映射用)。 */
    private final WarehouseMapper warehouseMapper;

    /**
     * 构造服务。
     *
     * @param itemService           物品服务
     * @param supplierService       供应商服务
     * @param customerService       客户服务
     * @param stockQueryService     库存查询服务
     * @param inboundService        入库单服务
     * @param outboundService       出库单服务
     * @param purchaseOrderService  采购订单服务
     * @param salesOrderService     销售订单服务
     * @param warehouseMapper       仓库 Mapper
     */
    public ExportServiceImpl(ItemService itemService, SupplierService supplierService,
            CustomerService customerService, StockQueryService stockQueryService,
            InboundService inboundService, OutboundService outboundService,
            PurchaseOrderService purchaseOrderService, SalesOrderService salesOrderService,
            WarehouseMapper warehouseMapper) {
        this.itemService = itemService;
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.stockQueryService = stockQueryService;
        this.inboundService = inboundService;
        this.outboundService = outboundService;
        this.purchaseOrderService = purchaseOrderService;
        this.salesOrderService = salesOrderService;
        this.warehouseMapper = warehouseMapper;
    }

    /**
     * 物品导出。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportItems(ItemQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<ItemVO> vos = itemService.list(query).rows();
        List<ItemExportRow> rows = vos.stream().map(this::toItemRow).toList();
        ExcelSupport.writeXlsx(resp, "items", "物品", "物品", ItemExportRow.class, rows);
    }

    /**
     * 供应商导出。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportSuppliers(SupplierQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<SupplierVO> vos = supplierService.list(query).rows();
        List<SupplierExportRow> rows = vos.stream().map(this::toSupplierRow).toList();
        ExcelSupport.writeXlsx(resp, "suppliers", "供应商", "供应商", SupplierExportRow.class, rows);
    }

    /**
     * 客户导出。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportCustomers(CustomerQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<CustomerVO> vos = customerService.list(query).rows();
        List<CustomerExportRow> rows = vos.stream().map(this::toCustomerRow).toList();
        ExcelSupport.writeXlsx(resp, "customers", "客户", "客户", CustomerExportRow.class, rows);
    }

    /**
     * 库存导出(沿用库存查询的数据权限)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportStock(StockQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<StockVO> vos = stockQueryService.queryStock(query).rows();
        List<StockExportRow> rows = vos.stream().map(this::toStockRow).toList();
        ExcelSupport.writeXlsx(resp, "stock", "库存", "库存", StockExportRow.class, rows);
    }

    /**
     * 入库单导出(头信息一行一单)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportInbound(InboundDocQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<InboundDocVO> vos = inboundService.list(query).rows();
        List<InboundExportRow> rows = vos.stream().map(this::toInboundRow).toList();
        ExcelSupport.writeXlsx(resp, "inbound", "入库单", "入库单", InboundExportRow.class, rows);
    }

    /**
     * 出库单导出(头信息一行一单)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportOutbound(OutboundDocQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<OutboundDocVO> vos = outboundService.list(query).rows();
        List<OutboundExportRow> rows = vos.stream().map(this::toOutboundRow).toList();
        ExcelSupport.writeXlsx(resp, "outbound", "出库单", "出库单", OutboundExportRow.class, rows);
    }

    /**
     * 采购订单导出(头信息一行一单)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportPurchaseOrders(PurchaseOrderQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<PurchaseOrderVO> vos = purchaseOrderService.list(query).rows();
        List<PurchaseOrderExportRow> rows = vos.stream().map(this::toPurchaseRow).toList();
        ExcelSupport.writeXlsx(resp, "purchase-orders", "采购订单", "采购订单",
                PurchaseOrderExportRow.class, rows);
    }

    /**
     * 销售订单导出(头信息一行一单,发货仓库 ID 批量映射名称)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应
     */
    @Override
    public void exportSalesOrders(SalesOrderQuery query, HttpServletResponse resp) {
        query.setPage(1);
        query.setPageSize(ExcelSupport.EXPORT_PAGE_SIZE);
        List<SalesOrderVO> vos = salesOrderService.list(query).rows();
        Set<Long> whIds = new HashSet<>();
        for (SalesOrderVO vo : vos) {
            if (vo.warehouseId() != null) {
                whIds.add(vo.warehouseId());
            }
        }
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, String> whNames = whIds.isEmpty()
                ? Map.of()
                : warehouseMapper.selectByIds(whIds).stream()
                        .collect(Collectors.toMap(WarehouseDO::getId, WarehouseDO::getWarehouseName));
        List<SalesOrderExportRow> rows = vos.stream()
                .map(vo -> toSalesRow(vo, whNames))
                .toList();
        ExcelSupport.writeXlsx(resp, "sales-orders", "销售订单", "销售订单", SalesOrderExportRow.class, rows);
    }

    /**
     * 物品 VO 转导出行。
     *
     * @param vo 物品 VO
     * @return 导出行
     */
    private ItemExportRow toItemRow(ItemVO vo) {
        ItemExportRow row = new ItemExportRow();
        row.setItemCode(vo.itemCode());
        row.setItemName(vo.itemName());
        row.setUnit(vo.unit());
        row.setSpec(vo.spec());
        row.setCategory(vo.category());
        row.setBarcode(vo.barcode());
        row.setSecondUnit(vo.secondUnit());
        row.setConvertFactor(toStr(vo.convertFactor()));
        row.setBrand(vo.brand());
        row.setOrigin(vo.origin());
        row.setMinStock(toStr(vo.minStock()));
        row.setDefaultTaxRate(toStr(vo.defaultTaxRate()));
        row.setReferencePurchasePrice(toStr(vo.referencePurchasePrice()));
        row.setReferenceSalePrice(toStr(vo.referenceSalePrice()));
        row.setCreatedAt(formatDateTime(vo.createdAt()));
        return row;
    }

    /**
     * 供应商 VO 转导出行。
     *
     * @param vo 供应商 VO
     * @return 导出行
     */
    private SupplierExportRow toSupplierRow(SupplierVO vo) {
        SupplierExportRow row = new SupplierExportRow();
        row.setSupplierCode(vo.supplierCode());
        row.setSupplierName(vo.supplierName());
        row.setContact(vo.contact());
        row.setPhone(vo.phone());
        row.setAddress(vo.address());
        row.setSettleMethod(ImportExportLabels.settleToCn(vo.settleMethod()));
        row.setDefaultTaxRate(toStr(vo.defaultTaxRate()));
        row.setTaxNo(vo.taxNo());
        row.setEmail(vo.email());
        row.setBankName(vo.bankName());
        row.setBankAccount(vo.bankAccount());
        row.setCreditLimit(toStr(vo.creditLimit()));
        row.setPayTermDays(vo.payTermDays() == null ? null : String.valueOf(vo.payTermDays()));
        row.setDeliveryAddress(vo.deliveryAddress());
        row.setCreatedAt(formatDateTime(vo.createdAt()));
        return row;
    }

    /**
     * 客户 VO 转导出行。
     *
     * @param vo 客户 VO
     * @return 导出行
     */
    private CustomerExportRow toCustomerRow(CustomerVO vo) {
        CustomerExportRow row = new CustomerExportRow();
        row.setCustomerCode(vo.customerCode());
        row.setCustomerName(vo.customerName());
        row.setContact(vo.contact());
        row.setPhone(vo.phone());
        row.setAddress(vo.address());
        row.setSettleMethod(ImportExportLabels.settleToCn(vo.settleMethod()));
        row.setDefaultTaxRate(toStr(vo.defaultTaxRate()));
        row.setTaxNo(vo.taxNo());
        row.setEmail(vo.email());
        row.setBankName(vo.bankName());
        row.setBankAccount(vo.bankAccount());
        row.setCreditLimit(toStr(vo.creditLimit()));
        row.setPayTermDays(vo.payTermDays() == null ? null : String.valueOf(vo.payTermDays()));
        row.setDeliveryAddress(vo.deliveryAddress());
        row.setCreatedAt(formatDateTime(vo.createdAt()));
        return row;
    }

    /**
     * 库存 VO 转导出行。
     *
     * @param vo 库存 VO
     * @return 导出行
     */
    private StockExportRow toStockRow(StockVO vo) {
        StockExportRow row = new StockExportRow();
        ItemVO item = vo.item();
        row.setItemCode(item == null ? null : item.itemCode());
        row.setItemName(item == null ? null : item.itemName());
        row.setSpec(item == null ? null : item.spec());
        row.setUnit(item == null ? null : item.unit());
        row.setWarehouseName(vo.warehouse() == null ? null : vo.warehouse().warehouseName());
        row.setBatchNo(vo.batch() == null ? null : vo.batch().batchNo());
        row.setLocationCode(vo.location() == null ? null : vo.location().locationCode());
        row.setQuantity(vo.quantity());
        row.setPreAllocatedQty(vo.preAllocatedQty());
        row.setAvailableQty(vo.availableQty());
        return row;
    }

    /**
     * 入库单 VO 转导出行。
     *
     * @param vo 入库单 VO
     * @return 导出行
     */
    private InboundExportRow toInboundRow(InboundDocVO vo) {
        InboundExportRow row = new InboundExportRow();
        row.setDocNo(vo.docNo());
        row.setDocDate(formatDate(vo.docDate()));
        row.setDocType(vo.docType());
        row.setStatus(ImportExportLabels.docStatusToCn(vo.status()));
        row.setRefDocNo(vo.refDocNo());
        row.setSupplierName(vo.supplierName());
        row.setWarehouseName(vo.warehouse() == null ? null : vo.warehouse().warehouseName());
        row.setCarrier(vo.carrier());
        row.setFreight(vo.freight());
        row.setTotalAmount(vo.totalAmount());
        row.setRemark(vo.remark());
        return row;
    }

    /**
     * 出库单 VO 转导出行。
     *
     * @param vo 出库单 VO
     * @return 导出行
     */
    private OutboundExportRow toOutboundRow(OutboundDocVO vo) {
        OutboundExportRow row = new OutboundExportRow();
        row.setDocNo(vo.docNo());
        row.setDocDate(formatDate(vo.docDate()));
        row.setDocType(vo.docType());
        row.setStatus(ImportExportLabels.docStatusToCn(vo.status()));
        row.setRefDocNo(vo.refDocNo());
        row.setCustomerName(vo.customerName());
        row.setWarehouseName(vo.warehouse() == null ? null : vo.warehouse().warehouseName());
        row.setCarrier(vo.carrier());
        row.setFreight(vo.freight());
        row.setTotalAmount(vo.totalAmount());
        row.setRemark(vo.remark());
        return row;
    }

    /**
     * 采购订单 VO 转导出行。
     *
     * @param vo 采购订单 VO
     * @return 导出行
     */
    private PurchaseOrderExportRow toPurchaseRow(PurchaseOrderVO vo) {
        PurchaseOrderExportRow row = new PurchaseOrderExportRow();
        row.setDocNo(vo.docNo());
        row.setDocDate(formatDate(vo.docDate()));
        row.setStatus(ImportExportLabels.docStatusToCn(vo.status()));
        row.setSupplierName(vo.supplier() == null ? null : vo.supplier().supplierName());
        row.setContractNo(vo.contractNo());
        row.setCurrencyCode(vo.currencyCode());
        row.setFreight(vo.freight());
        row.setShippingAddress(vo.shippingAddress());
        row.setDiscountAmount(vo.discountAmount());
        row.setTotalAmount(vo.totalAmount());
        row.setTotalTaxAmount(vo.totalTaxAmount());
        row.setTotalTaxInclusive(vo.totalTaxInclusive());
        row.setRemark(vo.remark());
        return row;
    }

    /**
     * 销售订单 VO 转导出行。
     *
     * @param vo       销售订单 VO
     * @param whNames  仓库 ID → 名称映射
     * @return 导出行
     */
    private SalesOrderExportRow toSalesRow(SalesOrderVO vo, Map<Long, String> whNames) {
        SalesOrderExportRow row = new SalesOrderExportRow();
        row.setDocNo(vo.docNo());
        row.setDocDate(formatDate(vo.docDate()));
        row.setStatus(ImportExportLabels.docStatusToCn(vo.status()));
        row.setCustomerName(vo.customer() == null ? null : vo.customer().customerName());
        row.setWarehouseName(vo.warehouseId() == null ? null : whNames.get(vo.warehouseId()));
        row.setContractNo(vo.contractNo());
        row.setCurrencyCode(vo.currencyCode());
        row.setFreight(vo.freight());
        row.setShippingAddress(vo.shippingAddress());
        row.setDiscountAmount(vo.discountAmount());
        row.setTotalAmount(vo.totalAmount());
        row.setTotalTaxAmount(vo.totalTaxAmount());
        row.setTotalTaxInclusive(vo.totalTaxInclusive());
        row.setRemark(vo.remark());
        return row;
    }

    /**
     * BigDecimal 转字符串(null 返回 null)。
     *
     * @param value 数值(可空)
     * @return 字符串或 null
     */
    private String toStr(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    /**
     * 日期格式化(yyyy-MM-dd,null 返回 null)。
     *
     * @param date 日期(可空)
     * @return 格式化字符串或 null
     */
    private String formatDate(LocalDate date) {
        return date == null ? null : DATE_FMT.format(date);
    }

    /**
     * 时间格式化(yyyy-MM-dd HH:mm:ss,null 返回 null)。
     *
     * @param time 时间(可空)
     * @return 格式化字符串或 null
     */
    private String formatDateTime(LocalDateTime time) {
        return time == null ? null : DATE_TIME_FMT.format(time);
    }
}
