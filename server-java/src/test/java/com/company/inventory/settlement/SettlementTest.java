package com.company.inventory.settlement;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.model.dto.inbound.InboundCreateDTO;
import com.company.inventory.model.dto.inbound.InboundLineDTO;
import com.company.inventory.model.dto.outbound.OutboundCreateDTO;
import com.company.inventory.model.dto.outbound.OutboundLineDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderLineDTO;
import com.company.inventory.model.dto.returns.PurchaseReturnCreateDTO;
import com.company.inventory.model.dto.returns.PurchaseReturnLineDTO;
import com.company.inventory.model.dto.sales.SalesOrderCreateDTO;
import com.company.inventory.model.dto.sales.SalesOrderLineDTO;
import com.company.inventory.model.dto.settlement.InvoiceCreateDTO;
import com.company.inventory.model.dto.settlement.InvoiceItemDTO;
import com.company.inventory.model.dto.settlement.InvoiceUpdateDTO;
import com.company.inventory.model.dto.settlement.PaymentCreateDTO;
import com.company.inventory.model.dto.settlement.PaymentLineDTO;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.InvoiceQuery;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;
import com.company.inventory.model.vo.returns.PurchaseReturnCreatedVO;
import com.company.inventory.model.vo.sales.SalesOrderVO;
import com.company.inventory.model.vo.settlement.InvoiceVO;
import com.company.inventory.model.vo.settlement.LedgerRowVO;
import com.company.inventory.model.vo.settlement.PaymentVO;
import com.company.inventory.model.vo.settlement.SettlementDashboardVO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.InvoiceService;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.PaymentService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.PurchaseReturnService;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.SettlementService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结算域测试(V18 三单匹配 + 应收应付):发票三态/差异/防超开/作废释放、退货自动生成
 * 红字凭单、付款部分核销/防超核/方向校验、台账与订单执行聚合、权限。
 *
 * <p>每用例前置清空业务表(主数据保留),台账断言走独立 SQL 基准对拍。</p>
 *
 * @author inventory
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123"
})
class SettlementTest {

    /** 发票服务 */
    @Autowired
    private InvoiceService invoiceService;
    /** 付款/收款服务 */
    @Autowired
    private PaymentService paymentService;
    /** 结算台账服务 */
    @Autowired
    private SettlementService settlementService;
    /** 采购订单服务 */
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    /** 销售订单服务 */
    @Autowired
    private SalesOrderService salesOrderService;
    /** 入库服务 */
    @Autowired
    private InboundService inboundService;
    /** 出库服务 */
    @Autowired
    private OutboundService outboundService;
    /** 采购退货服务 */
    @Autowired
    private PurchaseReturnService purchaseReturnService;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 供应商 Mapper */
    @Autowired
    private SupplierMapper supplierMapper;
    /** 客户 Mapper */
    @Autowired
    private CustomerMapper customerMapper;
    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;
    /** JDBC(独立 SQL 基准) */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** HTTP 权限验证 */
    @Autowired
    private TestRestTemplate rest;

    /** 普通仓库 */
    private long warehouseId;
    /** 物品 */
    private long itemId;
    /** 制单人(operator) */
    private long creatorUserId;
    /** 审批人(admin) */
    private long approverUserId;
    /** viewer 用户名(权限用例) */
    private static final String VIEWER = "settle_viewer";

    /** 唯一编码种子 */
    private long seq = 0;

    /**
     * 前置:清库并造主数据(一仓一物三用户)。
     */
    @BeforeAll
    void cleanDb() {
        // 全清(含主数据):测试类可能被多次加载,防止编码唯一约束撞历史残留
        truncateBusiness();
        jdbcTemplate.execute("TRUNCATE \"location\",\"item\",\"warehouse\",\"supplier\",\"customer\","
                + "\"sys_user\" RESTART IDENTITY CASCADE");
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("ST-WH");
        wh.setWarehouseName("结算测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();
        ItemDO item = new ItemDO();
        item.setItemCode("ST-IT");
        item.setItemName("结算测试物");
        item.setUnit("件");
        item.setSpec("原规格");
        itemMapper.insert(item);
        itemId = item.getId();
        creatorUserId = insertUser("settle_creator", "结算制单人", "operator",
                "settle123");
        approverUserId = insertUser("settle_approver", "结算审批人", "admin", "settle123");
        insertUser(VIEWER, "结算查看员", "viewer", "settle123");
    }

    /**
     * 每用例前置:清空业务单据与库存(主数据保留),台账断言不受历史干扰。
     */
    @BeforeEach
    void resetBusiness() {
        truncateBusiness();
    }

    /**
     * 用例 1:手工发票平账 → draft → confirm → confirmed,应付台账/仪表盘含它。
     */
    @Test
    void manualInvoiceBalanceAndLedger() {
        long supplierId = insertSupplier("平账供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "100.00", "13.00");
        arrive(order, "10");
        long inLineId = inboundLineId(order.id());
        BigDecimal inclusive = new BigDecimal("1130.00");

        InvoiceVO created = invoiceService.create(new InvoiceCreateDTO("purchase", supplierId,
                LocalDate.now(), "平账测试", List.of(new InvoiceItemDTO("inbound",
                inboundDocId(order.id()), inLineId, inclusive))), "settle_creator");
        assertEquals("draft", created.status(), "全行平账应为 draft");
        assertTrue(created.docNo().startsWith("FP-"), "发票号应以 FP- 开头: " + created.docNo());

        InvoiceVO confirmed = invoiceService.confirm(created.id(), "settle_creator");
        assertEquals("confirmed", confirmed.status());

        // 台账:独立 SQL 基准对拍
        LedgerRowVO row = apLedgerRow(supplierId);
        assertNotNull(row);
        assertEquals(0, inclusive.compareTo(row.invoicedAmount()), "台账已开票应=1130");
        assertEquals(0, BigDecimal.ZERO.compareTo(row.settledAmount()), "未付款应=0");
        assertEquals(0, inclusive.compareTo(row.balance()), "应付余额应=1130");
        SettlementDashboardVO dash = settlementService.dashboard();
        assertEquals(0, inclusive.compareTo(dash.apBalance()), "仪表盘应付余额应=1130");
    }

    /**
     * 用例 2:差异发票(开票额 < 含税额,|variance|>0.01)→ mismatch,confirm 400;改平后 draft 可 confirm。
     */
    @Test
    void mismatchInvoiceFlow() {
        long supplierId = insertSupplier("差异供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "100.00", "13.00");
        arrive(order, "10");
        long inLineId = inboundLineId(order.id());
        long docId = inboundDocId(order.id());

        // 开票 1100 < 含税额 1130,差异 -30 → mismatch(未超开)
        InvoiceVO created = invoiceService.create(new InvoiceCreateDTO("purchase", supplierId,
                LocalDate.now(), null, List.of(new InvoiceItemDTO("inbound", docId, inLineId,
                new BigDecimal("1100.00")))), "settle_creator");
        assertEquals("mismatch", created.status(), "超差应落 mismatch");
        assertThrows(BizException.class,
                () -> invoiceService.confirm(created.id(), "settle_creator"));

        // 改平(1130)→ draft → confirm
        InvoiceVO updated = invoiceService.update(created.id(), new InvoiceUpdateDTO(
                null, null, null, List.of(new InvoiceItemDTO("inbound", docId, inLineId,
                new BigDecimal("1130.00")))), "settle_creator");
        assertEquals("draft", updated.status());
        InvoiceVO confirmed = invoiceService.confirm(created.id(), "settle_creator");
        assertEquals("confirmed", confirmed.status());
        // 已确认不可再改
        assertThrows(BizException.class, () -> invoiceService.update(created.id(),
                new InvoiceUpdateDTO(null, null, null, List.of(new InvoiceItemDTO("inbound",
                        docId, inLineId, new BigDecimal("1130.00")))),
                "settle_creator"));
    }

    /**
     * 用例 3:防超开——开票额超含税额 400;同一源行已有未作废发票再开 400;作废释放后可再开。
     */
    @Test
    void overInvoiceGuardAndVoidRelease() {
        long supplierId = insertSupplier("防超开供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "100.00", "13.00");
        arrive(order, "10");
        long inLineId = inboundLineId(order.id());
        long docId = inboundDocId(order.id());

        // 开票额 1200 > 含税额 1130 → 400
        assertThrows(BizException.class, () -> invoiceService.create(
                new InvoiceCreateDTO("purchase", supplierId, LocalDate.now(), null,
                        List.of(new InvoiceItemDTO("inbound", docId, inLineId,
                                new BigDecimal("1200.00")))), "settle_creator"));

        // 开满 1130 → 再开同一源行 400(占用)
        InvoiceVO full = invoiceService.create(new InvoiceCreateDTO("purchase", supplierId,
                LocalDate.now(), null,
                List.of(new InvoiceItemDTO("inbound", docId, inLineId,
                        new BigDecimal("1130.00")))), "settle_creator");
        assertThrows(BizException.class, () -> invoiceService.create(
                new InvoiceCreateDTO("purchase", supplierId, LocalDate.now(), null,
                        List.of(new InvoiceItemDTO("inbound", docId, inLineId,
                                new BigDecimal("1.00")))), "settle_creator"));

        // 作废释放 → 可再开
        InvoiceVO voided = invoiceService.voidInvoice(full.id(), "settle_creator");
        assertEquals("voided", voided.status());
        InvoiceVO again = invoiceService.create(new InvoiceCreateDTO("purchase", supplierId,
                LocalDate.now(), null,
                List.of(new InvoiceItemDTO("inbound", docId, inLineId,
                        new BigDecimal("1130.00")))), "settle_creator");
        assertEquals("draft", again.status());
    }

    /**
     * 用例 4:独立入库行(ref_type 空)挂票 → 400。
     */
    @Test
    void standaloneInboundRejected() {
        long supplierId = insertSupplier("独立入库供应商");
        inboundService.create(new InboundCreateDTO(warehouseId, "结算独立入库",
                List.of(new InboundLineDTO(itemId, new BigDecimal("5"), null, null, null,
                        null, null, null, null, null, null)),
                null, null, LocalDate.now()), "settle_creator");
        Long inDocId = jdbcTemplate.queryForObject(
                "SELECT id FROM inbound_doc WHERE ref_type IS NULL ORDER BY id DESC LIMIT 1",
                Long.class);
        Long inLineId = jdbcTemplate.queryForObject(
                "SELECT id FROM inbound_doc_item WHERE doc_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, inDocId);
        assertThrows(BizException.class, () -> invoiceService.create(
                new InvoiceCreateDTO("purchase", supplierId, LocalDate.now(), null,
                        List.of(new InvoiceItemDTO("inbound", inDocId, inLineId,
                                new BigDecimal("100.00")))), "settle_creator"));
    }

    /**
     * 用例 5:采购退货过账 → 自动生成负数草稿发票(金额=−退货含税额,ref_return_id 正确),
     * 可改金额,确认后净核减应付。
     */
    @Test
    void returnGeneratesNegativeInvoice() {
        long supplierId = insertSupplier("红字凭单供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "20.00", "13.00");
        arrive(order, "10");
        long lineId = order.items().get(0).id();

        PurchaseReturnCreatedVO ret = purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), "退货",
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("4"),
                                null, null))), "settle_creator");

        // 自动生成负票:金额 = -(4×20×1.13) = -90.40,draft,ref_return_id 正确
        InvoiceVO neg = invoiceService.get(invoiceIdOfReturn(ret.id()));
        assertEquals("negative", neg.sign());
        assertEquals("draft", neg.status());
        assertEquals("return_gen", neg.sourceType());
        assertEquals(ret.id(), neg.refReturnId());
        assertEquals(0, new BigDecimal("-90.40").compareTo(neg.totalAmount()));
        assertEquals(1, neg.items().size());
        assertEquals("purchase_return", neg.items().get(0).srcDocType());
        assertEquals(0, new BigDecimal("-90.40").compareTo(neg.items().get(0).invoicedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(neg.items().get(0).variance()));

        // 可改金额(改小 80)
        InvoiceVO updated = invoiceService.update(neg.id(), new InvoiceUpdateDTO(null, null,
                null, List.of(new InvoiceItemDTO(neg.items().get(0).srcDocType(),
                neg.items().get(0).srcDocId(), neg.items().get(0).srcDocItemId(),
                new BigDecimal("-80.00")))), "settle_creator");
        assertEquals("mismatch", updated.status(), "-80 对源 -90.40 超差应 mismatch");
        // 负票不能新增行(src 指向不存在的行)
        assertThrows(BizException.class, () -> invoiceService.update(neg.id(),
                new InvoiceUpdateDTO(null, null, null,
                        List.of(new InvoiceItemDTO("inbound", 1L, 999999L, new BigDecimal("-1")))),
                "settle_creator"));

        // 确认(改回平)→ 台账净开票 -80
        invoiceService.update(neg.id(), new InvoiceUpdateDTO(null, null, null,
                List.of(new InvoiceItemDTO("purchase_return",
                        neg.items().get(0).srcDocId(), neg.items().get(0).srcDocItemId(),
                        new BigDecimal("-90.40")))), "settle_creator");
        InvoiceVO confirmed = invoiceService.confirm(neg.id(), "settle_creator");
        assertEquals("confirmed", confirmed.status());
        LedgerRowVO row = apLedgerRow(supplierId);
        assertEquals(0, new BigDecimal("-90.40").compareTo(row.invoicedAmount()),
                "台账已开票净额应=-90.40");
        assertEquals(0, new BigDecimal("-90.40").compareTo(row.balance()), "应付余额应=-90.40");
    }

    /**
     * 用例 6:部分付款——100 元票付 40,余额 60;再付 80 → 400(超核);付 60 后结清。
     */
    @Test
    void partialPaymentAndOverSettleGuard() {
        long supplierId = insertSupplier("部分付款供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "1", "100.00", "0");
        arrive(order, "1");
        long invoiceId = confirmedInvoice(supplierId, "100.00", order);

        PaymentVO first = paymentService.create(new PaymentCreateDTO("payment", supplierId,
                LocalDate.now(), "付40",
                List.of(new PaymentLineDTO(invoiceId, new BigDecimal("40.00")))),
                "settle_creator");
        assertTrue(first.docNo().startsWith("FK-"), "付款单号应以 FK- 开头: " + first.docNo());
        assertEquals(0, new BigDecimal("40.00").compareTo(first.totalAmount()));
        assertEquals("confirmed", first.status());

        LedgerRowVO row = apLedgerRow(supplierId);
        assertEquals(0, new BigDecimal("40.00").compareTo(row.settledAmount()));
        assertEquals(0, new BigDecimal("60.00").compareTo(row.balance()), "余额应=60");

        // 再付 80 > 剩余 60 → 400
        assertThrows(BizException.class, () -> paymentService.create(
                new PaymentCreateDTO("payment", supplierId, LocalDate.now(), null,
                        List.of(new PaymentLineDTO(invoiceId, new BigDecimal("80.00")))),
                "settle_creator"));
        // 付满 60 → 余额 0
        paymentService.create(new PaymentCreateDTO("payment", supplierId, LocalDate.now(), null,
                List.of(new PaymentLineDTO(invoiceId, new BigDecimal("60.00")))),
                "settle_creator");
        assertEquals(0, BigDecimal.ZERO.compareTo(apLedgerRow(supplierId).balance()));
    }

    /**
     * 用例 7:方向校验——付款核销售票 400、收款核采购票 400。
     */
    @Test
    void paymentDirectionGuard() {
        long supplierId = insertSupplier("方向供应商");
        long customerA = insertCustomer("方向客户");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "1", "50.00", "0");
        arrive(order, "1");
        long purchaseInvoice = confirmedInvoice(supplierId, "50.00", order);
        SalesOrderVO so = approvedShippedSalesOrder(customerA, "1", "60.00", "0");
        long salesInvoice = confirmedSalesInvoiceById(customerA, "60.00", so);

        // 付款核销售票 → 400
        assertThrows(BizException.class, () -> paymentService.create(
                new PaymentCreateDTO("payment", customerA, LocalDate.now(), null,
                        List.of(new PaymentLineDTO(salesInvoice, new BigDecimal("10")))),
                "settle_creator"));
        // 收款核采购票 → 400
        assertThrows(BizException.class, () -> paymentService.create(
                new PaymentCreateDTO("receipt", supplierId, LocalDate.now(), null,
                        List.of(new PaymentLineDTO(purchaseInvoice, new BigDecimal("10")))),
                "settle_creator"));
    }

    /**
     * 用例 8:台账聚合正确性——独立 SQL 基准(入库额/开票额/付款额/余额)对拍 API。
     */
    @Test
    void ledgerAggregationBaseline() {
        long supplierA = insertSupplier("基准供应商A");
        long supplierB = insertSupplier("基准供应商B");
        long customerA = insertCustomer("基准客户A");
        // A:入库 1130,开票 1130,付 500 → 余额 630
        PurchaseOrderVO orderA = approvedPurchaseOrder(supplierA, "10", "100.00", "13.00");
        arrive(orderA, "10");
        long invA = confirmedInvoice(supplierA, "1130.00", orderA);
        paymentService.create(new PaymentCreateDTO("payment", supplierA, LocalDate.now(), null,
                List.of(new PaymentLineDTO(invA, new BigDecimal("500.00")))), "settle_creator");
        // B:无业务,全 0
        // 客户A:出库 565(5×100×1.13),开票 565,收 100 → 余额 465
        SalesOrderVO so = approvedShippedSalesOrder(customerA, "5", "100.00", "13.00");
        long invC = confirmedSalesInvoiceById(customerA, "565.00", so);
        paymentService.create(new PaymentCreateDTO("receipt", customerA, LocalDate.now(), null,
                List.of(new PaymentLineDTO(invC, new BigDecimal("100.00")))), "settle_creator");

        // 独立 SQL 基准(与 API 不同路径的手写 SQL)
        BigDecimal baseInbound = sql("SELECT COALESCE(SUM(i.tax_inclusive_total),0) "
                + "FROM inbound_doc_item i JOIN inbound_doc d ON d.id = i.doc_id "
                + "JOIN purchase_order po ON po.id = d.ref_doc_id "
                + "WHERE d.ref_type = 'purchase' AND po.supplier_id = ?", supplierA);
        BigDecimal baseInvoiced = sql("SELECT COALESCE(SUM(total_amount),0) FROM invoice "
                + "WHERE invoice_type = 'purchase' AND party_id = ? AND status = 'confirmed'",
                supplierA);
        BigDecimal basePaid = sql("SELECT COALESCE(SUM(pl.amount),0) FROM payment_line pl "
                + "JOIN payment_doc pd ON pd.id = pl.payment_id "
                + "WHERE pd.status = 'confirmed' AND pd.pay_type = 'payment' "
                + "AND pd.party_id = ?", supplierA);

        LedgerRowVO rowA = apLedgerRow(supplierA);
        assertEquals(0, baseInbound.compareTo(rowA.receivedAmount()), "入库额对拍");
        assertEquals(0, baseInvoiced.compareTo(rowA.invoicedAmount()), "开票额对拍");
        assertEquals(0, basePaid.compareTo(rowA.settledAmount()), "付款额对拍");
        assertEquals(0, baseInvoiced.subtract(basePaid).compareTo(rowA.balance()), "余额对拍");
        assertEquals(0, baseInbound.subtract(baseInvoiced).compareTo(rowA.estimatedAmount()),
                "待开票暂估=入库-开票");
        LedgerRowVO rowB = apLedgerRow(supplierB);
        assertEquals(0, BigDecimal.ZERO.compareTo(rowB.invoicedAmount()), "无业务供应商应 0");

        // 应收侧
        LedgerRowVO arRow = arLedgerRow(customerA);
        assertEquals(0, new BigDecimal("565.00").compareTo(arRow.receivedAmount()));
        assertEquals(0, new BigDecimal("565.00").compareTo(arRow.invoicedAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(arRow.settledAmount()));
        assertEquals(0, new BigDecimal("465.00").compareTo(arRow.balance()));
        SettlementDashboardVO dash = settlementService.dashboard();
        assertEquals(0, new BigDecimal("630.00").compareTo(dash.apBalance()));
        assertEquals(0, new BigDecimal("465.00").compareTo(dash.arBalance()));
    }

    /**
     * 用例 9:订单执行跟踪四列(下单/入库/开票/付款)数值正确。
     */
    @Test
    void orderProgressColumns() {
        long supplierId = insertSupplier("执行跟踪供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "100.00", "13.00");
        arrive(order, "10");
        long invId = confirmedInvoice(supplierId, "1130.00", order);
        paymentService.create(new PaymentCreateDTO("payment", supplierId, LocalDate.now(), null,
                List.of(new PaymentLineDTO(invId, new BigDecimal("500.00")))), "settle_creator");

        LedgerRowVO row = apLedgerRow(supplierId);
        assertEquals(1, row.orders().size());
        com.company.inventory.model.vo.settlement.OrderProgressVO progress = row.orders().get(0);
        assertEquals(order.id(), progress.orderId());
        assertEquals(0, new BigDecimal("1130.00").compareTo(progress.orderAmount()), "下单额");
        assertEquals(0, new BigDecimal("1130.00").compareTo(progress.receivedAmount()), "入库额");
        assertEquals(0, new BigDecimal("1130.00").compareTo(progress.invoicedAmount()), "开票额");
        assertEquals(0, new BigDecimal("500.00").compareTo(progress.settledAmount()), "付款额");
    }

    /**
     * 用例 10:负票(红字凭单)不被付款核销 → 400。
     */
    @Test
    void negativeInvoiceCannotSettle() {
        long supplierId = insertSupplier("负票核销供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "20.00", "13.00");
        arrive(order, "10");
        long lineId = order.items().get(0).id();
        PurchaseReturnCreatedVO ret = purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("4"),
                                null, null))), "settle_creator");
        long negInvoiceId = invoiceIdOfReturn(ret.id());
        invoiceService.confirm(negInvoiceId, "settle_creator");
        assertThrows(BizException.class, () -> paymentService.create(
                new PaymentCreateDTO("payment", supplierId, LocalDate.now(), null,
                        List.of(new PaymentLineDTO(negInvoiceId, new BigDecimal("10.00")))),
                "settle_creator"));
        // 未确认票核销同样 400(开票额=含税额 226,平账 draft)
        long draftInvoice = invoiceService.create(new InvoiceCreateDTO("purchase", supplierId,
                LocalDate.now(), null, List.of(new InvoiceItemDTO("inbound",
                inboundDocId(order.id()), inboundLineId(order.id()),
                new BigDecimal("226.00")))), "settle_creator").id();
        assertThrows(BizException.class, () -> paymentService.create(
                new PaymentCreateDTO("payment", supplierId, LocalDate.now(), null,
                        List.of(new PaymentLineDTO(draftInvoice, new BigDecimal("10.00")))),
                "settle_creator"));
    }

    /**
     * 用例 11:权限——viewer POST /invoices 403,GET /invoices 200。
     */
    @Test
    @SuppressWarnings("unchecked")
    void viewerPermission() {
        long supplierId = insertSupplier("权限供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "10.00", "0");
        arrive(order, "10");
        invoiceService.create(new InvoiceCreateDTO("purchase", supplierId, LocalDate.now(), null,
                List.of(new InvoiceItemDTO("inbound", inboundDocId(order.id()),
                        inboundLineId(order.id()), new BigDecimal("100.00")))),
                "settle_creator");

        String token = login(VIEWER, "settle123");
        assertTrue(token != null && !token.isBlank(), "viewer 登录应返回 token");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        // viewer 读 200 且可见
        ResponseEntity<Map> getRes = rest.exchange("/api/v1/invoices", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertEquals(200, getRes.getStatusCode().value());
        // viewer 写 403
        String body = "{\"invoiceType\":\"purchase\",\"partyId\":" + supplierId
                + ",\"invoiceDate\":\"2026-01-01\",\"items\":[{\"srcDocType\":\"inbound\","
                + "\"srcDocId\":1,\"srcDocItemId\":1,\"invoicedAmount\":1}]}";
        ResponseEntity<Map> postRes = rest.exchange("/api/v1/invoices", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(403, postRes.getStatusCode().value());
    }

    /**
     * 用例 12:数据权限口径与采购订单列表一致——发票/付款列表均无仓库维度过滤(全可见)。
     */
    @Test
    void dataScopeConsistentWithPurchaseOrder() {
        long supplierId = insertSupplier("口径供应商");
        PurchaseOrderVO order = approvedPurchaseOrder(supplierId, "10", "10.00", "0");
        arrive(order, "10");
        invoiceService.create(new InvoiceCreateDTO("purchase", supplierId, LocalDate.now(), null,
                List.of(new InvoiceItemDTO("inbound", inboundDocId(order.id()),
                        inboundLineId(order.id()), new BigDecimal("100.00")))),
                "settle_creator");
        InvoiceVO inv = invoiceService.list(new InvoiceQuery()).rows().get(0);
        invoiceService.confirm(inv.id(), "settle_creator");
        paymentService.create(new PaymentCreateDTO("payment", supplierId, LocalDate.now(), null,
                List.of(new PaymentLineDTO(inv.id(), new BigDecimal("100.00")))), "settle_creator");

        // 未授权任何仓库(DataScope 空列表):发票/付款/采购订单列表均不受影响(与采购订单同口径)
        try {
            DataScope.set(List.of());
            InvoiceQuery iq = new InvoiceQuery();
            PageResult<InvoiceVO> invoices = invoiceService.list(iq);
            assertTrue(invoices.total() >= 1, "发票列表不应受仓库数据权限过滤");
            com.company.inventory.model.query.PaymentQuery pq =
                    new com.company.inventory.model.query.PaymentQuery();
            assertTrue(paymentService.list(pq).total() >= 1, "付款列表不应受仓库数据权限过滤");
            com.company.inventory.model.query.PurchaseOrderQuery poq =
                    new com.company.inventory.model.query.PurchaseOrderQuery();
            assertTrue(purchaseOrderService.list(poq).total() >= 1,
                    "采购订单列表同口径(基准)");
        } finally {
            DataScope.clear();
        }
    }

    // ==================== 数据构造辅助 ====================

    /**
     * 清空业务单据与库存(结算四表 + 单据链 + 库存)。
     */
    private void truncateBusiness() {
        jdbcTemplate.execute("TRUNCATE \"payment_line\",\"invoice_item\",\"payment_doc\",\"invoice\","
                + "\"purchase_return_item\",\"sales_return_item\",\"purchase_return\",\"sales_return\","
                + "\"purchase_order_item\",\"sales_order_item\",\"outbound_doc_item\",\"inbound_doc_item\","
                + "\"purchase_order\",\"sales_order\",\"outbound_doc\",\"inbound_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\" RESTART IDENTITY CASCADE");
    }

    /**
     * 造供应商。
     *
     * @param name 名称
     * @return 供应商 ID
     */
    private long insertSupplier(String name) {
        SupplierDO sp = new SupplierDO();
        sp.setSupplierCode("ST-SP" + (seq++));
        sp.setSupplierName(name);
        sp.setStatus(1);
        supplierMapper.insert(sp);
        return sp.getId();
    }

    /**
     * 造客户。
     *
     * @param name 名称
     * @return 客户 ID
     */
    private long insertCustomer(String name) {
        CustomerDO c = new CustomerDO();
        c.setCustomerCode("ST-CK" + (seq++));
        c.setCustomerName(name);
        c.setStatus(1);
        customerMapper.insert(c);
        return c.getId();
    }

    /**
     * 造用户(真实 bcrypt 密码,权限用例登录用)。
     *
     * @param username 用户名
     * @param name     姓名
     * @param role     角色
     * @param password 明文密码
     * @return 用户 ID
     */
    private long insertUser(String username, String name, String role, String password) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(password));
        user.setName(name);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 新建并审批通过采购订单(单行)。
     *
     * @param supplierId 供应商 ID
     * @param qty        数量
     * @param price      不含税单价
     * @param rate       税率(%)
     * @return 订单 VO
     */
    private PurchaseOrderVO approvedPurchaseOrder(long supplierId, String qty, String price,
            String rate) {
        PurchaseOrderVO vo = purchaseOrderService.create(new PurchaseOrderCreateDTO(
                LocalDate.now(), supplierId, creatorUserId, BigDecimal.ZERO, "结算测试",
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal(qty), null,
                        new BigDecimal(price), new BigDecimal(rate), null))), "settle_creator");
        purchaseOrderService.submit(vo.id(), "settle_creator");
        return purchaseOrderService.approve(vo.id(), "settle_approver");
    }

    /**
     * 采购到货(refType=purchase,全额,价税快照随单)。
     *
     * @param order 订单
     * @param qty   到货量
     */
    private void arrive(PurchaseOrderVO order, String qty) {
        long lineId = order.items().get(0).id();
        inboundService.create(new InboundCreateDTO(
                warehouseId, "结算到货",
                List.of(new InboundLineDTO(itemId, new BigDecimal(qty), null, null, null,
                        null, null, null, null, null, lineId)),
                "purchase", order.id(), LocalDate.now()), "settle_creator");
    }

    /**
     * 新建已审批且全额发货的销售订单(先手工备货防超卖)。
     *
     * @param customerId 客户 ID
     * @param qty        数量
     * @param price      不含税单价
     * @param rate       税率(%)
     * @return 订单 VO
     */
    private SalesOrderVO approvedShippedSalesOrder(long customerId, String qty, String price,
            String rate) {
        inboundService.create(new InboundCreateDTO(warehouseId, "结算备货",
                List.of(new InboundLineDTO(itemId, new BigDecimal(qty), null, null, null,
                        null, null, null, null, null, null)),
                null, null, LocalDate.now()), "settle_creator");
        SalesOrderVO vo = salesOrderService.create(new SalesOrderCreateDTO(
                LocalDate.now(), customerId, creatorUserId, warehouseId, "结算测试",
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal(qty), null,
                        new BigDecimal(price), new BigDecimal(rate), null))), "settle_creator");
        salesOrderService.submit(vo.id(), "settle_creator");
        vo = salesOrderService.approve(vo.id(), "settle_approver");
        long lineId = vo.items().get(0).id();
        outboundService.create(new OutboundCreateDTO(
                warehouseId, "结算发货",
                List.of(new OutboundLineDTO(itemId, new BigDecimal(qty), null, null,
                        null, null, null, lineId)),
                "sales", vo.id(), LocalDate.now()), "settle_creator");
        return salesOrderService.get(vo.id());
    }

    /**
     * 对指定采购订单的入库行开确认发票(含税额全额)。
     *
     * @param supplierId 供应商 ID
     * @param amount     开票额
     * @param order      采购订单
     * @return 发票 ID
     */
    private long confirmedInvoice(long supplierId, String amount, PurchaseOrderVO order) {
        return invoiceService.confirm(invoiceService.create(new InvoiceCreateDTO("purchase",
                supplierId, LocalDate.now(), null,
                List.of(new InvoiceItemDTO("inbound", inboundDocId(order.id()),
                        inboundLineId(order.id()), new BigDecimal(amount)))),
                "settle_creator").id(), "settle_creator").id();
    }

    /**
     * 对指定销售订单的出库行开确认发票。
     *
     * @param customerId 客户 ID
     * @param amount     开票额
     * @param order      销售订单
     * @return 发票 ID
     */
    private long confirmedSalesInvoiceById(long customerId, String amount, SalesOrderVO order) {
        Long docId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_doc WHERE ref_type = 'sales' AND ref_doc_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, order.id());
        Long lineId = jdbcTemplate.queryForObject(
                "SELECT id FROM outbound_doc_item WHERE doc_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, docId);
        long id = invoiceService.create(new InvoiceCreateDTO("sales", customerId,
                LocalDate.now(), null, List.of(new InvoiceItemDTO("outbound", docId, lineId,
                        new BigDecimal(amount)))), "settle_creator").id();
        return invoiceService.confirm(id, "settle_creator").id();
    }

    /**
     * 取订单关联入库单 ID。
     *
     * @param orderId 采购订单 ID
     * @return 入库单 ID
     */
    private long inboundDocId(long orderId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM inbound_doc WHERE ref_type = 'purchase' AND ref_doc_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, orderId);
        assertNotNull(id);
        return id;
    }

    /**
     * 取订单关联入库行 ID。
     *
     * @param orderId 采购订单 ID
     * @return 入库行 ID
     */
    private long inboundLineId(long orderId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT i.id FROM inbound_doc_item i JOIN inbound_doc d ON d.id = i.doc_id "
                        + "WHERE d.ref_type = 'purchase' AND d.ref_doc_id = ? "
                        + "ORDER BY i.id DESC LIMIT 1", Long.class, orderId);
        assertNotNull(id);
        return id;
    }

    /**
     * 退货单自动生成的发票 ID。
     *
     * @param returnId 退货单 ID
     * @return 发票 ID
     */
    private long invoiceIdOfReturn(long returnId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM invoice WHERE ref_return_id = ? LIMIT 1", Long.class, returnId);
        assertNotNull(id, "退货过账应自动生成发票");
        return id;
    }

    /**
     * 应付台账中取某供应商行。
     *
     * @param supplierId 供应商 ID
     * @return 台账行
     */
    private LedgerRowVO apLedgerRow(long supplierId) {
        for (LedgerRowVO row : settlementService.apLedger()) {
            if (supplierId == row.partyId()) {
                return row;
            }
        }
        return null;
    }

    /**
     * 应收台账中取某客户行。
     *
     * @param customerId 客户 ID
     * @return 台账行
     */
    private LedgerRowVO arLedgerRow(long customerId) {
        for (LedgerRowVO row : settlementService.arLedger()) {
            if (customerId == row.partyId()) {
                return row;
            }
        }
        return null;
    }

    /**
     * 独立 SQL 基准查询。
     *
     * @param sql   SQL
     * @param args  参数
     * @return 结果
     */
    private BigDecimal sql(String sql, Object... args) {
        BigDecimal v = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 登录并返回 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return token
     */
    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> json = res.getBody();
        return json == null ? null : String.valueOf(json.get("token"));
    }
}
