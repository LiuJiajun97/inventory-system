package com.company.inventory.returns;

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
import com.company.inventory.model.dto.returns.SalesReturnCreateDTO;
import com.company.inventory.model.dto.returns.SalesReturnLineDTO;
import com.company.inventory.model.dto.sales.SalesOrderCreateDTO;
import com.company.inventory.model.dto.sales.SalesOrderLineDTO;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.PurchaseOrderItemMapper;
import com.company.inventory.mapper.SalesOrderItemMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.InboundDocQuery;
import com.company.inventory.model.query.OutboundDocQuery;
import com.company.inventory.model.query.PurchaseReturnQuery;
import com.company.inventory.model.query.SalesReturnQuery;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.PurchaseReturnService;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.SalesReturnService;
import com.company.inventory.model.vo.inbound.InboundDocVO;
import com.company.inventory.model.vo.outbound.OutboundDocVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;
import com.company.inventory.model.vo.returns.PurchaseReturnCreatedVO;
import com.company.inventory.model.vo.returns.PurchaseReturnVO;
import com.company.inventory.model.vo.returns.SalesReturnCreatedVO;
import com.company.inventory.model.vo.returns.SalesReturnVO;
import com.company.inventory.model.vo.sales.SalesOrderVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退货模块测试:采购退货/销售退货 create 即过账(联动出入库)+ 可退量上限 +
 * 原单行已退回写 + 序列号复用出库校验 + 列表数据权限与分页规范。
 *
 * @author inventory
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123"
})
class ReturnDocTest {

    /** 采购退货单服务 */
    @Autowired
    private PurchaseReturnService purchaseReturnService;
    /** 销售退货单服务 */
    @Autowired
    private SalesReturnService salesReturnService;
    /** 采购订单服务 */
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    /** 销售订单服务 */
    @Autowired
    private SalesOrderService salesOrderService;
    /** 入库单服务(到货) */
    @Autowired
    private InboundService inboundService;
    /** 出库单服务(发货) */
    @Autowired
    private OutboundService outboundService;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 供应商 Mapper */
    @Autowired
    private SupplierMapper supplierMapper;
    /** 客户 Mapper */
    @Autowired
    private CustomerMapper customerMapper;
    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;
    /** 采购订单行 Mapper */
    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;
    /** 销售订单行 Mapper */
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** 序列号 Mapper */
    @Autowired
    private SerialMapper serialMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 普通仓库(无批次/序列号/库位) */
    private long warehouseId;
    /** 序列号仓库 */
    private long serialWarehouseId;
    /** 物品 */
    private long itemId;
    /** 制单人 */
    private long creatorUserId;
    /** 审批人 */
    private long approverUserId;

    /** 唯一编码种子 */
    private long seq = 0;

    /**
     * 前置:清库并造基础主数据(两仓一物两用户)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_return_item\",\"sales_return_item\","
                + "\"purchase_return\",\"sales_return\","
                + "\"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"outbound_doc_item\",\"inbound_doc_item\","
                + "\"purchase_order\",\"sales_order\",\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"outbound_doc\",\"inbound_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        warehouseId = insertWarehouse("RT-WH", false);
        serialWarehouseId = insertWarehouse("RT-SWH", true);
        ItemDO item = new ItemDO();
        item.setItemCode("RT-IT");
        item.setItemName("退货测试物");
        item.setUnit("件");
        item.setSpec("原规格");
        itemMapper.insert(item);
        itemId = item.getId();
        creatorUserId = insertUser("ret_creator", "退货制单人", "operator");
        approverUserId = insertUser("ret_approver", "退货审批人", "admin");
    }

    /**
     * 每用例前置:清空库存/流水/序列号/批次(用例间隔离)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.execute("TRUNCATE \"stock\", \"stock_transaction\", \"serial\", \"batch\"");
    }

    /**
     * 用例 1:采购退货正常过账 → 库存减少、原行 returned_qty 正确、金额快照正确、
     * 联动出库单 ref_type=purchase_return 且 ref_doc_id=退货单 ID。
     */
    @Test
    void purchaseReturnNormal() {
        PurchaseOrderVO order = approvedPurchaseOrder("10", "20.00", "13.00");
        arrive(order, warehouseId, "10", null);
        long lineId = order.items().get(0).id();
        assertEquals(0, new BigDecimal("10").compareTo(stockQty(warehouseId)));

        PurchaseReturnCreatedVO vo = purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), "退货",
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("4"),
                                null, null))), "ret_creator");
        assertNotNull(vo.id());
        assertTrue(vo.docNo().startsWith("CT-"), "退货单号应以 CT- 开头: " + vo.docNo());
        assertEquals("finished", vo.status());
        assertTrue(vo.outDocNo().startsWith("CK-"), "联动出库单号应以 CK- 开头: " + vo.outDocNo());

        // 库存 10 - 4 = 6
        assertEquals(0, new BigDecimal("6").compareTo(stockQty(warehouseId)));
        // 原行已退累计 = 4
        assertEquals(0, new BigDecimal("4").compareTo(returnedQtyOf(lineId)));
        // 金额快照:4 × 20 = 80;税 = 80 × 13% = 10.4;价税合计 = 90.4
        PurchaseReturnVO doc = purchaseReturnService.get(vo.id());
        assertEquals(0, new BigDecimal("80").compareTo(doc.totalAmount()));
        assertEquals(order.docNo(), doc.purchaseOrderNo());
        assertEquals(1, doc.items().size());
        assertEquals(0, new BigDecimal("80").compareTo(new BigDecimal(doc.items().get(0).amount())));
        assertEquals(0, new BigDecimal("10.4").compareTo(new BigDecimal(doc.items().get(0).taxAmount())));
        assertEquals(0, new BigDecimal("90.4").compareTo(new BigDecimal(doc.items().get(0).taxInclusiveTotal())));
        // 联动出库单:ref_type=purchase_return,ref_doc_id=退货单 ID
        Long outCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbound_doc WHERE ref_type = 'purchase_return'"
                        + " AND ref_doc_id = ? AND status = 'finished'", Long.class, vo.id());
        assertEquals(1L, outCount);
    }

    /**
     * 用例 2:采购退货超量(> 可退量)→ 拒绝,库存/核销零变化。
     */
    @Test
    void purchaseReturnOverRejected() {
        PurchaseOrderVO order = approvedPurchaseOrder("10", "20.00", "13.00");
        arrive(order, warehouseId, "10", null);
        long lineId = order.items().get(0).id();
        Long before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_return", Long.class);
        assertThrows(BizException.class, () -> purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("11"),
                                null, null))), "ret_creator"));
        assertEquals(0, new BigDecimal("10").compareTo(stockQty(warehouseId)));
        assertEquals(0, BigDecimal.ZERO.compareTo(returnedQtyOf(lineId)));
        Long docCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_return", Long.class);
        assertEquals(before, docCount);
    }

    /**
     * 用例 3:部分退两次 → 第一次退 3、第二次退剩余 7,第三次再退 0.01 被拒。
     */
    @Test
    void purchaseReturnPartialTwice() {
        PurchaseOrderVO order = approvedPurchaseOrder("10", "5.00", "0");
        arrive(order, warehouseId, "10", null);
        long lineId = order.items().get(0).id();
        purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("3"),
                                null, null))), "ret_creator");
        assertEquals(0, new BigDecimal("3").compareTo(returnedQtyOf(lineId)));
        assertEquals(0, new BigDecimal("7").compareTo(stockQty(warehouseId)));

        purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("7"),
                                null, null))), "ret_creator");
        assertEquals(0, new BigDecimal("10").compareTo(returnedQtyOf(lineId)));
        assertEquals(0, BigDecimal.ZERO.compareTo(stockQty(warehouseId)));

        // 可退数量已 0,再退 0.01 被拒
        assertThrows(BizException.class, () -> purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("0.01"),
                                null, null))), "ret_creator"));
        assertEquals(0, BigDecimal.ZERO.compareTo(stockQty(warehouseId)));
    }

    /**
     * 用例 4:销售退货正常过账 → 库存增加、原行 returned_qty 正确、shipped_qty 不变、
     * 联动入库单 ref_type=sales_return。
     */
    @Test
    void salesReturnNormal() {
        SalesOrderVO order = approvedShippedSalesOrder("10", "30.00", "13.00");
        long lineId = order.items().get(0).id();
        // 全额发货后库存 0
        assertEquals(0, BigDecimal.ZERO.compareTo(stockQty(warehouseId)));
        BigDecimal shippedBefore = shippedQtyOf(lineId);

        SalesReturnCreatedVO vo = salesReturnService.create(
                new SalesReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), "销退",
                        List.of(new SalesReturnLineDTO(lineId, new BigDecimal("4"),
                                null, null, null, null, null))), "ret_creator");
        assertNotNull(vo.id());
        assertTrue(vo.docNo().startsWith("XT-"), "退货单号应以 XT- 开头: " + vo.docNo());
        assertTrue(vo.inDocNo().startsWith("RK-"), "联动入库单号应以 RK- 开头: " + vo.inDocNo());

        // 库存 0 + 4 = 4
        assertEquals(0, new BigDecimal("4").compareTo(stockQty(warehouseId)));
        // 原行已退累计 = 4,发货累计不变
        assertEquals(0, new BigDecimal("4").compareTo(salesReturnedQtyOf(lineId)));
        assertEquals(0, shippedBefore.compareTo(shippedQtyOf(lineId)));
        // 金额快照:4 × 30 = 120
        SalesReturnVO doc = salesReturnService.get(vo.id());
        assertEquals(0, new BigDecimal("120").compareTo(doc.totalAmount()));
        assertEquals(order.docNo(), doc.salesOrderNo());
        Long inCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inbound_doc WHERE ref_type = 'sales_return'"
                        + " AND ref_doc_id = ? AND status = 'finished'", Long.class, vo.id());
        assertEquals(1L, inCount);
    }

    /**
     * 用例 5:销售退货超量(> shipped - returned)→ 拒绝。
     */
    @Test
    void salesReturnOverRejected() {
        SalesOrderVO order = approvedShippedSalesOrder("10", "30.00", "13.00");
        long lineId = order.items().get(0).id();
        salesReturnService.create(
                new SalesReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new SalesReturnLineDTO(lineId, new BigDecimal("4"),
                                null, null, null, null, null))), "ret_creator");
        // 可退 10 - 4 = 6,退 6.01 被拒
        assertThrows(BizException.class, () -> salesReturnService.create(
                new SalesReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new SalesReturnLineDTO(lineId, new BigDecimal("6.01"),
                                null, null, null, null, null))), "ret_creator"));
        assertEquals(0, new BigDecimal("4").compareTo(salesReturnedQtyOf(lineId)));
        assertEquals(0, new BigDecimal("4").compareTo(stockQty(warehouseId)));
    }

    /**
     * 用例 6:退货关联未审批/不存在的原单 → 拒绝。
     */
    @Test
    void returnAgainstBadOrderRejected() {
        // 草稿采购单(未提交未审批)
        long draftId = createPurchaseOrder("5", "10.00", "13.00").id();
        long draftLineId = purchaseOrderService.get(draftId).items().get(0).id();
        assertThrows(BizException.class, () -> purchaseReturnService.create(
                new PurchaseReturnCreateDTO(draftId, warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(draftLineId, new BigDecimal("1"),
                                null, null))), "ret_creator"));
        // 不存在的采购单
        assertThrows(BizException.class, () -> purchaseReturnService.create(
                new PurchaseReturnCreateDTO(999999L, warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(1L, new BigDecimal("1"),
                                null, null))), "ret_creator"));
        // 不存在的销售单
        assertThrows(BizException.class, () -> salesReturnService.create(
                new SalesReturnCreateDTO(999999L, warehouseId, LocalDate.now(), null,
                        List.of(new SalesReturnLineDTO(1L, new BigDecimal("1"),
                                null, null, null, null, null))), "ret_creator"));
        // 同一原单行重复出现在退货行 → 拒绝
        PurchaseOrderVO order = approvedPurchaseOrder("5", "10.00", "13.00");
        arrive(order, warehouseId, "5", null);
        long lineId = order.items().get(0).id();
        assertThrows(BizException.class, () -> purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("1"), null, null),
                                new PurchaseReturnLineDTO(lineId, new BigDecimal("1"), null, null))),
                "ret_creator"));
    }

    /**
     * 用例 7:序列号仓库退货 → 不传 serialNos 拒绝(复用现有出库校验);
     * 传了 → 过账成功且序列号台账 ref_out_doc_no = 联动出库单号。
     */
    @Test
    void serialPurchaseReturn() {
        PurchaseOrderVO order = approvedPurchaseOrder("2", "50.00", "13.00");
        arrive(order, serialWarehouseId, "2", List.of("RT-SN-1", "RT-SN-2"));

        // 不传序列号 → 现有出库校验拒绝
        long lineId = order.items().get(0).id();
        assertThrows(BizException.class, () -> purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), serialWarehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("1"),
                                null, null))), "ret_creator"));
        assertEquals(0, new BigDecimal("2").compareTo(stockQty(serialWarehouseId)));

        // 传序列号 → 过账成功
        PurchaseReturnCreatedVO vo = purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), serialWarehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("1"),
                                null, List.of("RT-SN-1")))), "ret_creator");
        assertEquals(0, new BigDecimal("1").compareTo(stockQty(serialWarehouseId)));
        assertEquals(0, new BigDecimal("1").compareTo(returnedQtyOf(lineId)));
        // 台账:RT-SN-1 已出库且 ref_out_doc_no = 联动出库单号
        SerialDO sn1 = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getSerialNo, "RT-SN-1"));
        assertNotNull(sn1);
        assertEquals("out", sn1.getStatus());
        assertEquals(vo.outDocNo(), sn1.getRefOutDocNo());
        // 未退的 RT-SN-2 仍在库
        SerialDO sn2 = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getSerialNo, "RT-SN-2"));
        assertNotNull(sn2);
        assertEquals("in_stock", sn2.getStatus());
    }

    /**
     * 用例 8:退货单列表数据权限(未授权查空/授权仓过滤/admin 豁免)+ 分页规范
     * {rows,total,page,pageSize}。
     */
    @Test
    void listDataScopeAndPage() {
        PurchaseOrderVO order = approvedPurchaseOrder("10", "20.00", "13.00");
        arrive(order, warehouseId, "10", null);
        long lineId = order.items().get(0).id();
        purchaseReturnService.create(
                new PurchaseReturnCreateDTO(order.id(), warehouseId, LocalDate.now(), null,
                        List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("2"),
                                null, null))), "ret_creator");
        // 自包含:另造一单销售退货供列表断言
        SalesOrderVO so = approvedShippedSalesOrder("5", "10.00", "13.00");
        long soLineId = so.items().get(0).id();
        salesReturnService.create(
                new SalesReturnCreateDTO(so.id(), warehouseId, LocalDate.now(), null,
                        List.of(new SalesReturnLineDTO(soLineId, new BigDecimal("1"),
                                null, null, null, null, null))), "ret_creator");

        PurchaseReturnQuery query = new PurchaseReturnQuery();
        // 未授权任何仓库 → 查空
        try {
            DataScope.set(List.of(999999L));
            PageResult<PurchaseReturnVO> none = purchaseReturnService.list(query);
            assertEquals(0L, none.total());
            assertTrue(none.rows().isEmpty());
            // 分页规范:rows/total/page/pageSize
            assertEquals(1L, none.page());
            assertEquals(20L, none.pageSize());
        } finally {
            DataScope.clear();
        }
        // 授权本仓 → 可见且全部属于本仓
        try {
            DataScope.set(List.of(warehouseId));
            PageResult<PurchaseReturnVO> scoped = purchaseReturnService.list(query);
            assertTrue(scoped.total() >= 1);
            for (PurchaseReturnVO vo : scoped.rows()) {
                assertEquals(warehouseId, vo.warehouseId());
            }
            assertEquals(query.getPage(), scoped.page());
            assertEquals(query.getPageSize(), scoped.pageSize());
        } finally {
            DataScope.clear();
        }
        // 销售退货单列表同语义(admin 豁免:未设置时全可见)
        SalesReturnQuery sQuery = new SalesReturnQuery();
        PageResult<SalesReturnVO> sAll = salesReturnService.list(sQuery);
        assertTrue(sAll.total() >= 1);
        assertEquals(sQuery.getPage(), sAll.page());
    }

    /**
     * 用例 9:序列号退货回流——已出库序列号经销售退货入库回置 in_stock,
     * 无 (item_id, serial_no) 唯一键冲突(修复前 insert 撞唯一键 400 必挂)。
     */
    @Test
    void salesReturnSerialReflow() {
        String sn = "REFLOW-SN-X";
        // 1) 序列号仓手工入库 SN-X(in_stock)
        inboundService.create(new InboundCreateDTO(serialWarehouseId, "回流测试备货",
                List.of(new InboundLineDTO(itemId, new BigDecimal("1"), null, null, null,
                        null, null, List.of(sn), null, null, null, null)),
                null, null, LocalDate.now()), "ret_creator");
        // 2) 建单审批销售订单(序列号仓)
        CustomerDO customer = new CustomerDO();
        customer.setCustomerCode("RT-CK" + (seq++));
        customer.setCustomerName("序列回流客户");
        customer.setStatus(1);
        customerMapper.insert(customer);
        SalesOrderVO order = salesOrderService.create(new SalesOrderCreateDTO(
                LocalDate.now(), customer.getId(), creatorUserId, serialWarehouseId, "序列回流",
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal("1"), null,
                        new BigDecimal("20.00"), null, new BigDecimal("13.00"), null))), "ret_creator");
        salesOrderService.submit(order.id(), "ret_creator");
        order = salesOrderService.approve(order.id(), "ret_approver");
        long lineId = order.items().get(0).id();
        // 3) 带序列号发货 → SN-X 变 out
        outboundService.create(new OutboundCreateDTO(serialWarehouseId, "回流测试发货",
                List.of(new OutboundLineDTO(itemId, new BigDecimal("1"), null, null,
                        List.of(sn), null, null, null, lineId)),
                "sales", order.id(), LocalDate.now()), "ret_creator");
        SerialDO outSn = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getSerialNo, sn));
        assertNotNull(outSn);
        assertEquals("out", outSn.getStatus());
        // 4) 销售退货把 SN-X 退回(修复前此处 insert 撞唯一键 400)
        SalesReturnCreatedVO vo = salesReturnService.create(
                new SalesReturnCreateDTO(order.id(), serialWarehouseId, LocalDate.now(), "序列回流退货",
                        List.of(new SalesReturnLineDTO(lineId, new BigDecimal("1"),
                                null, null, null, null, List.of(sn)))), "ret_creator");
        assertNotNull(vo.id());
        assertTrue(vo.inDocNo().startsWith("RK-"), "联动入库单号应以 RK- 开头: " + vo.inDocNo());
        // 5) 台账:SN-X 回置 in_stock,仓库=序列号仓,outbound_time 清空,库存 1
        SerialDO back = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getSerialNo, sn));
        assertNotNull(back);
        assertEquals("in_stock", back.getStatus());
        assertEquals(serialWarehouseId, back.getWarehouseId());
        assertNotNull(back.getInboundTime());
        assertNull(back.getOutboundTime());
        assertEquals(0, new BigDecimal("1").compareTo(stockQty(serialWarehouseId)));
    }

    /**
     * 用例 10:入库列表关联单号按 refType 分表回填(撞号回归)。
     * 造 refType=sales_return 的入库单(销售退货过账联动),再造同号采购订单制造撞号:
     * 修复前入库端无脑查采购订单表,会把 refDocNo 回填成同号采购订单的 CG 号;
     * 修复后必须按 refType=sales_return 查销售退货表,回填 XT 号与客户名。
     */
    @Test
    void inboundListRefNoFollowsRefType() {
        // 1) 已审批已发货的销售订单(自造客户,名称用于断言对端名)
        manualInbound(warehouseId, "10");
        CustomerDO customer = new CustomerDO();
        customer.setCustomerCode("RT-CK" + (seq++));
        customer.setCustomerName("撞号测试客户");
        customer.setStatus(1);
        customerMapper.insert(customer);
        SalesOrderVO so = salesOrderService.create(new SalesOrderCreateDTO(
                LocalDate.now(), customer.getId(), creatorUserId, warehouseId, "撞号测试",
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal("10"), null,
                        new BigDecimal("30.00"), null, new BigDecimal("13.00"), null))), "ret_creator");
        salesOrderService.submit(so.id(), "ret_creator");
        so = salesOrderService.approve(so.id(), "ret_approver");
        long soLineId = so.items().get(0).id();
        outboundService.create(new OutboundCreateDTO(warehouseId, "撞号测试发货",
                List.of(new OutboundLineDTO(itemId, new BigDecimal("10"), null, null,
                        null, null, null, null, soLineId)),
                "sales", so.id(), LocalDate.now()), "ret_creator");
        // 2) 销售退货过账 → 联动入库单 ref_type=sales_return,ref_doc_id=退货单 ID
        SalesReturnCreatedVO ret = salesReturnService.create(new SalesReturnCreateDTO(
                so.id(), warehouseId, LocalDate.now(), "撞号测试销退",
                List.of(new SalesReturnLineDTO(soLineId, new BigDecimal("2"),
                        null, null, null, null, null))), "ret_creator");
        long returnId = ret.id();
        assertTrue(ret.docNo().startsWith("XT-"), "退货单号应以 XT- 开头: " + ret.docNo());
        // 3) 造同号采购订单(自增 id 撞号场景;行只增不删,循环必然命中)
        while (countByRefId(returnId) == 0L) {
            createPurchaseOrder("1", "1.00", "0");
        }
        String clashDocNo = jdbcTemplate.queryForObject(
                "SELECT doc_no FROM purchase_order WHERE id = ?", String.class, returnId);
        assertTrue(clashDocNo.startsWith("CG-"), "撞号采购订单号应以 CG- 开头: " + clashDocNo);
        // 4) 入库列表 VO:关联单号必须是退货单 XT 号、对端名是客户名,而不是同号采购订单
        InboundDocQuery query = new InboundDocQuery();
        query.setDocNo(ret.inDocNo());
        PageResult<InboundDocVO> page = inboundService.list(query);
        assertEquals(1, page.rows().size());
        InboundDocVO in = page.rows().get(0);
        assertEquals("sales_return", in.refType());
        assertEquals(Long.valueOf(returnId), in.refDocId());
        assertEquals(ret.docNo(), in.refDocNo());
        assertNotEquals(clashDocNo, in.refDocNo(), "不应回填成同号采购订单号");
        assertEquals("撞号测试客户", in.supplierName(), "对端名应为销售订单的客户名");
    }

    /**
     * 用例 11:出库列表关联单号按 refType 分表回填(撞号回归,入库用例 10 的镜像)。
     * 造 refType=purchase_return 的出库单(采购退货过账联动),再造同号销售订单制造撞号:
     * 修复前出库端无脑查销售订单表,会把 refDocNo 回填成同号销售订单的 XS 号;
     * 修复后必须按 refType=purchase_return 查采购退货表,回填 CT 号与供应商名。
     */
    @Test
    void outboundListRefNoFollowsRefType() {
        // 1) 已审批采购订单并全额到货(可退量 = 10)
        PurchaseOrderVO order = approvedPurchaseOrder("10", "20.00", "13.00");
        arrive(order, warehouseId, "10", null);
        long lineId = order.items().get(0).id();
        // 2) 采购退货过账 → 联动出库单 ref_type=purchase_return,ref_doc_id=退货单 ID
        PurchaseReturnCreatedVO ret = purchaseReturnService.create(new PurchaseReturnCreateDTO(
                order.id(), warehouseId, LocalDate.now(), "撞号测试采退",
                List.of(new PurchaseReturnLineDTO(lineId, new BigDecimal("3"), null, null))),
                "ret_creator");
        long returnId = ret.id();
        assertTrue(ret.docNo().startsWith("CT-"), "退货单号应以 CT- 开头: " + ret.docNo());
        // 3) 造同号销售订单(自增 id 撞号场景;行只增不删,循环必然命中)
        while (countSalesOrderByRefId(returnId) == 0L) {
            CustomerDO customer = new CustomerDO();
            customer.setCustomerCode("RT-CK" + (seq++));
            customer.setCustomerName("撞号测试销单客户");
            customer.setStatus(1);
            customerMapper.insert(customer);
            salesOrderService.create(new SalesOrderCreateDTO(
                    LocalDate.now(), customer.getId(), creatorUserId, warehouseId, "撞号测试销单",
                    List.of(new SalesOrderLineDTO(itemId, new BigDecimal("1"), null,
                            new BigDecimal("1.00"), null, new BigDecimal("0.00"), null))), "ret_creator");
        }
        String clashDocNo = jdbcTemplate.queryForObject(
                "SELECT doc_no FROM sales_order WHERE id = ?", String.class, returnId);
        assertTrue(clashDocNo.startsWith("XS-"), "撞号销售订单号应以 XS- 开头: " + clashDocNo);
        // 4) 出库列表 VO:关联单号必须是退货单 CT 号、对端名是供应商名,而不是同号销售订单
        OutboundDocQuery query = new OutboundDocQuery();
        query.setDocNo(ret.outDocNo());
        PageResult<OutboundDocVO> page = outboundService.list(query);
        assertEquals(1, page.rows().size());
        OutboundDocVO out = page.rows().get(0);
        assertEquals("purchase_return", out.refType());
        assertEquals(Long.valueOf(returnId), out.refDocId());
        assertEquals(ret.docNo(), out.refDocNo());
        assertNotEquals(clashDocNo, out.refDocNo(), "不应回填成同号销售订单号");
        assertEquals("退货测试供应商", out.customerName(), "对端名应为原采购订单的供应商名");
    }

    /**
     * 新建并审批通过采购订单(单价/税率/超收比例 0)。
     *
     * @param qty   订购数量
     * @param price 单价
     * @param rate  税率
     * @return 订单 VO(含行)
     */
    private PurchaseOrderVO approvedPurchaseOrder(String qty, String price, String rate) {
        PurchaseOrderVO vo = createPurchaseOrder(qty, price, rate);
        purchaseOrderService.submit(vo.id(), "ret_creator");
        return purchaseOrderService.approve(vo.id(), "ret_approver");
    }

    /**
     * 新建草稿采购订单。
     *
     * @param qty   订购数量
     * @param price 单价
     * @param rate  税率
     * @return 订单 VO
     */
    private PurchaseOrderVO createPurchaseOrder(String qty, String price, String rate) {
        SupplierDO sp = new SupplierDO();
        sp.setSupplierCode("RT-SP" + (seq++));
        sp.setSupplierName("退货测试供应商");
        sp.setStatus(1);
        supplierMapper.insert(sp);
        PurchaseOrderVO vo = purchaseOrderService.create(new PurchaseOrderCreateDTO(
                LocalDate.now(), sp.getId(), creatorUserId, BigDecimal.ZERO, "退货测试",
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal(qty), null,
                        new BigDecimal(price), null, new BigDecimal(rate), null))), "ret_creator");
        return purchaseOrderService.get(vo.id());
    }

    /**
     * 采购到货(refType=purchase 核销)。
     *
     * @param order     订单
     * @param whId      到货仓库 ID
     * @param qty       到货量
     * @param serialNos 序列号列表(序列号仓库必传,可空)
     */
    private void arrive(PurchaseOrderVO order, long whId, String qty, List<String> serialNos) {
        long lineId = order.items().get(0).id();
        inboundService.create(new InboundCreateDTO(
                whId, "退货测试到货",
                List.of(new InboundLineDTO(itemId, new BigDecimal(qty), null, null, null,
                        null, null, serialNos, null, null, null, lineId)),
                "purchase", order.id(), LocalDate.now()), "ret_creator");
    }

    /**
     * 手工入库(无关联单)。
     *
     * @param whId 仓库 ID
     * @param qty  数量
     */
    private void manualInbound(long whId, String qty) {
        inboundService.create(new InboundCreateDTO(whId, "退货测试备货",
                List.of(new InboundLineDTO(itemId, new BigDecimal(qty), null, null, null,
                        null, null, null, null, null, null, null)),
                null, null, LocalDate.now()), "ret_creator");
    }

    /**
     * 新建已审批且已全额发货的销售订单(先手工备货防超卖)。
     *
     * @param qty   订购/发货数量
     * @param price 单价
     * @param rate  税率
     * @return 订单 VO(含行)
     */
    private SalesOrderVO approvedShippedSalesOrder(String qty, String price, String rate) {
        manualInbound(warehouseId, qty);
        CustomerDO customer = new CustomerDO();
        customer.setCustomerCode("RT-CK" + (seq++));
        customer.setCustomerName("退货测试客户");
        customer.setStatus(1);
        customerMapper.insert(customer);
        SalesOrderVO vo = salesOrderService.create(new SalesOrderCreateDTO(
                LocalDate.now(), customer.getId(), creatorUserId, warehouseId, "退货测试",
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal(qty), null,
                        new BigDecimal(price), null, new BigDecimal(rate), null))), "ret_creator");
        salesOrderService.submit(vo.id(), "ret_creator");
        vo = salesOrderService.approve(vo.id(), "ret_approver");
        long lineId = vo.items().get(0).id();
        outboundService.create(new OutboundCreateDTO(
                warehouseId, "退货测试发货",
                List.of(new OutboundLineDTO(itemId, new BigDecimal(qty), null, null,
                        null, null, null, null, lineId)),
                "sales", vo.id(), LocalDate.now()), "ret_creator");
        return salesOrderService.get(vo.id());
    }

    /**
     * 采购订单表指定 id 行数(造撞号用)。
     *
     * @param id 目标 ID
     * @return 行数
     */
    private long countByRefId(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM purchase_order WHERE id = ?", Long.class, id);
    }

    /**
     * 销售订单表指定 id 行数(造撞号用)。
     *
     * @param id 目标 ID
     * @return 行数
     */
    private long countSalesOrderByRefId(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sales_order WHERE id = ?", Long.class, id);
    }

    /**
     * 采购订单行已退累计。
     *
     * @param lineId 行 ID
     * @return 已退量
     */
    private BigDecimal returnedQtyOf(long lineId) {
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT returned_qty FROM purchase_order_item WHERE id = ?", BigDecimal.class, lineId);
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 销售订单行发货累计。
     *
     * @param lineId 行 ID
     * @return 发货量
     */
    private BigDecimal shippedQtyOf(long lineId) {
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT shipped_qty FROM sales_order_item WHERE id = ?", BigDecimal.class, lineId);
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 销售订单行已退累计(直接读行表)。
     *
     * @param lineId 行 ID
     * @return 已退量
     */
    private BigDecimal salesReturnedQtyOf(long lineId) {
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT returned_qty FROM sales_order_item WHERE id = ?", BigDecimal.class, lineId);
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 仓库内该物品总库存。
     *
     * @param whId 仓库 ID
     * @return 总量
     */
    private BigDecimal stockQty(long whId) {
        List<StockDO> rows = stockMapper.selectList(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, whId).eq(StockDO::getItemId, itemId));
        BigDecimal total = BigDecimal.ZERO;
        for (StockDO row : rows) {
            total = total.add(row.getQuantity());
        }
        return total;
    }

    /**
     * 造仓库。
     *
     * @param code       编码
     * @param enableSerial 是否启用序列号
     * @return 仓库 ID
     */
    private long insertWarehouse(String code, boolean enableSerial) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + "仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(enableSerial);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        return wh.getId();
    }

    /**
     * 造用户。
     *
     * @param username 用户名
     * @param name     姓名
     * @param role     角色
     * @return 用户 ID
     */
    private long insertUser(String username, String name, String role) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash("$2a$10$dummy");
        user.setName(name);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }
}
