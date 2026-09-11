package com.company.inventory.purchase;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.dto.inbound.InboundCreateDTO;
import com.company.inventory.dto.inbound.InboundLineDTO;
import com.company.inventory.dto.purchase.ArrivalLine;
import com.company.inventory.dto.purchase.PurchaseActionDTO;
import com.company.inventory.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.dto.purchase.PurchaseOrderLineDTO;
import com.company.inventory.entity.item.ItemDO;
import com.company.inventory.entity.supplier.SupplierDO;
import com.company.inventory.entity.user.UserDO;
import com.company.inventory.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.vo.purchase.PurchaseOrderItemVO;
import com.company.inventory.vo.purchase.PurchaseOrderVO;
import com.company.inventory.vo.supplier.SupplierVO;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采购订单测试:状态机 + 价税三列一致性 + 部分到货/超收边界 + 物料快照不变 + 终态只读。
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
class PurchaseOrderTest {

    /** 采购订单服务 */
    @Autowired
    private PurchaseOrderService purchaseOrderService;
    /** 入库单服务(到货) */
    @Autowired
    private InboundService inboundService;
    /** 供应商 Mapper */
    @Autowired
    private SupplierMapper supplierMapper;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 订单 Mapper */
    @Autowired
    private PurchaseOrderMapper orderMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 制单人 */
    private long creatorUserId;
    /** 审批人 */
    private long approverUserId;
    /** 供应商 */
    private long supplierId;
    /** 物品 */
    private long itemId;
    /** 仓库 */
    private long warehouseId;

    /** 唯一编码种子 */
    private long seq = 0;

    /**
     * 每用例前置:清空库存/流水(用例间隔离)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.execute("TRUNCATE \"Stock\", \"StockTransaction\" RESTART IDENTITY");
    }

    /**
     * 前置:清库并造基础主数据(供应商/物品/用户/仓库)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"PurchaseOrderItem\",\"SalesOrderItem\",\"TransferDocItem\","
                + "\"StocktakeDocItem\",\"StockAdjustDocItem\",\"OutboundDocItem\",\"InboundDocItem\","
                + "\"PurchaseOrder\",\"SalesOrder\",\"TransferDoc\",\"StocktakeDoc\",\"StockAdjustDoc\","
                + "\"OutboundDoc\",\"InboundDoc\",\"StockTransaction\",\"Stock\",\"Serial\",\"Batch\","
                + "\"Location\",\"Item\",\"Warehouse\",\"Supplier\",\"Customer\",\"User\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("PO-WH");
        wh.setWarehouseName("采购测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("PO-IT");
        item.setItemName("采购测试物");
        item.setUnit("件");
        item.setSpec("原规格");
        itemMapper.insert(item);
        itemId = item.getId();

        SupplierDO sp = new SupplierDO();
        sp.setSupplierCode("PO-SP");
        sp.setSupplierName("采购测试供应商");
        sp.setStatus(1);
        supplierMapper.insert(sp);
        supplierId = sp.getId();

        creatorUserId = insertUser("po_creator", "制单人", "operator");
        approverUserId = insertUser("po_approver", "审批人", "admin");
    }

    /**
     * 用例 1:新建订单,行级与表头价税三列一致性(amount=qty*price, tax=amount*rate, 合计=Σ)。
     */
    @Test
    void createTotalsConsistency() {
        PurchaseOrderVO vo = createOrder("10", "99.50", "13.00");
        assertEquals("draft", vo.status());
        PurchaseOrderItemVO line = vo.items().get(0);
        // 10 × 99.5 = 995;税 = 995 × 13% = 129.35;价税合计 = 1124.35
        assertEquals(0, new BigDecimal("995").compareTo(new BigDecimal(line.amount())));
        assertEquals(0, new BigDecimal("129.35").compareTo(new BigDecimal(line.taxAmount())));
        assertEquals(0, new BigDecimal("1124.35").compareTo(new BigDecimal(line.taxInclusiveTotal())));
        assertEquals(0, new BigDecimal("995").compareTo(new BigDecimal(vo.totalAmount())));
        assertEquals(0, new BigDecimal("129.35").compareTo(new BigDecimal(vo.totalTaxAmount())));
        assertEquals(0, new BigDecimal("1124.35").compareTo(new BigDecimal(vo.totalTaxInclusive())));
    }

    /**
     * 用例 2:提交→待审批。
     */
    @Test
    void submitToPending() {
        long id = createOrder("1", "10", "13.00").id();
        PurchaseOrderVO vo = purchaseOrderService.submit(id, "po_creator");
        assertEquals("pending", vo.status());
    }

    /**
     * 用例 3:审批人=制单人 → 拒绝。
     */
    @Test
    void approveByCreatorRejected() {
        long id = createOrder("1", "10", "13.00").id();
        purchaseOrderService.submit(id, "po_creator");
        assertThrows(BizException.class, () -> purchaseOrderService.approve(id, "po_creator"));
    }

    /**
     * 用例 4:审批通过 + 重复审批幂等。
     */
    @Test
    void approveAndIdempotent() {
        long id = createOrder("1", "10", "13.00").id();
        purchaseOrderService.submit(id, "po_creator");
        PurchaseOrderVO vo = purchaseOrderService.approve(id, "po_approver");
        assertEquals("approved", vo.status());
        assertEquals("po_approver", vo.approver());
        assertNotNull(vo.approvedAt());
        PurchaseOrderVO again = purchaseOrderService.approve(id, "po_approver");
        assertEquals("approved", again.status());
    }

    /**
     * 用例 5:未提交不可审批。
     */
    @Test
    void approveWithoutSubmitRejected() {
        long id = createOrder("1", "10", "13.00").id();
        assertThrows(BizException.class, () -> purchaseOrderService.approve(id, "po_approver"));
    }

    /**
     * 用例 6:驳回必填原因;驳回后可编辑回 draft。
     */
    @Test
    void rejectRequiresReasonAndEditBackToDraft() {
        long id = createOrder("1", "10", "13.00").id();
        purchaseOrderService.submit(id, "po_creator");
        assertThrows(BizException.class, () ->
                purchaseOrderService.reject(id, new PurchaseActionDTO("  "), "po_approver"));
        PurchaseOrderVO rejected =
                purchaseOrderService.reject(id, new PurchaseActionDTO("价格不合适"), "po_approver");
        assertEquals("rejected", rejected.status());
        assertEquals("价格不合适", rejected.rejectReason());
        // rejected 不可再审批
        assertThrows(BizException.class, () -> purchaseOrderService.approve(id, "po_approver"));
        // 编辑后回 draft
        PurchaseOrderVO edited = purchaseOrderService.update(id,
                new PurchaseOrderCreateDTO(LocalDate.now(), supplierId, creatorUserId, null,
                        "改后", List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("2"),
                                null, new BigDecimal("5"), null, null))), "po_creator");
        assertEquals("draft", edited.status());
        assertEquals(0, new BigDecimal("10").compareTo(new BigDecimal(edited.totalAmount())));
    }

    /**
     * 用例 7:非草稿不可编辑(approved 只读)。
     */
    @Test
    void updateNonDraftRejected() {
        long id = createOrder("1", "10", "13.00").id();
        purchaseOrderService.submit(id, "po_creator");
        purchaseOrderService.approve(id, "po_approver");
        assertThrows(BizException.class, () -> purchaseOrderService.update(id,
                new PurchaseOrderCreateDTO(LocalDate.now(), supplierId, creatorUserId, null,
                        null, List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("1"),
                                null, new BigDecimal("1"), null, null))), "po_creator"));
    }

    /**
     * 用例 8:部分到货 2 次,累计回写;第二次到满 → 行 closed + 订单自动 completed。
     */
    @Test
    void partialArrivalAutoComplete() {
        long id = createOrder("10", "1", "13.00").id();
        purchaseOrderService.submit(id, "po_creator");
        purchaseOrderService.approve(id, "po_approver");
        long lineId = purchaseOrderService.get(id).items().get(0).id();

        inboundService.create(new InboundCreateDTO(warehouseId, "部分到货",
                List.of(new InboundLineDTO(itemId, new BigDecimal("4"), null, null, null,
                        null, null, null, null, null, lineId)),
                "purchase", id, LocalDate.now()), "po_creator");
        PurchaseOrderVO mid = purchaseOrderService.get(id);
        assertEquals("approved", mid.status());
        assertEquals(0, new BigDecimal("4").compareTo(new BigDecimal(mid.items().get(0).arrivedQty())));
        assertEquals(Boolean.FALSE, mid.items().get(0).closed());

        inboundService.create(new InboundCreateDTO(warehouseId, "到满",
                List.of(new InboundLineDTO(itemId, new BigDecimal("6"), null, null, null,
                        null, null, null, null, null, lineId)),
                "purchase", id, LocalDate.now()), "po_creator");
        PurchaseOrderVO done = purchaseOrderService.get(id);
        assertEquals("completed", done.status());
        assertEquals(Boolean.TRUE, done.items().get(0).closed());
        assertEquals(0, new BigDecimal("10").compareTo(new BigDecimal(done.items().get(0).arrivedQty())));
        // 库存 +10
        var stock = stockMapper.selectOne(new LambdaQueryWrapper<com.company.inventory.entity.stock.StockDO>()
                .eq(com.company.inventory.entity.stock.StockDO::getWarehouseId, warehouseId)
                .eq(com.company.inventory.entity.stock.StockDO::getItemId, itemId));
        assertNotNull(stock);
        assertEquals(0, new BigDecimal("10").compareTo(stock.getQuantity()));
    }

    /**
     * 用例 9:超收边界——恰好等于上限(10×1.1=11)通过。
     */
    @Test
    void overReceiptExactlyAtLimitPasses() {
        long id = createOrderWithRate("10", "0.1").id();
        purchaseOrderService.submit(id, "po_creator");
        purchaseOrderService.approve(id, "po_approver");
        long lineId = purchaseOrderService.get(id).items().get(0).id();
        inboundService.create(new InboundCreateDTO(warehouseId, "到上限",
                List.of(new InboundLineDTO(itemId, new BigDecimal("11"), null, null, null,
                        null, null, null, null, null, lineId)),
                "purchase", id, LocalDate.now()), "po_creator");
        PurchaseOrderVO vo = purchaseOrderService.get(id);
        assertEquals("completed", vo.status());
        assertEquals(0, new BigDecimal("11").compareTo(new BigDecimal(vo.items().get(0).arrivedQty())));
    }

    /**
     * 用例 10:超收边界——超上限 0.0001 拒绝。
     */
    @Test
    void overReceiptBeyondLimitRejected() {
        long id = createOrderWithRate("10", "0.1").id();
        purchaseOrderService.submit(id, "po_creator");
        purchaseOrderService.approve(id, "po_approver");
        long lineId = purchaseOrderService.get(id).items().get(0).id();
        BizException ex = assertThrows(BizException.class, () ->
                inboundService.create(new InboundCreateDTO(warehouseId, "超收",
                        List.of(new InboundLineDTO(itemId, new BigDecimal("11.0001"), null, null, null,
                                null, null, null, null, null, lineId)),
                        "purchase", id, LocalDate.now()), "po_creator"));
        assertTrue(ex.getMessage().contains("超收"));
        // 拒绝后库存无变化、订单行未回写
        var stock = stockMapper.selectList(new LambdaQueryWrapper<com.company.inventory.entity.stock.StockDO>()
                .eq(com.company.inventory.entity.stock.StockDO::getWarehouseId, warehouseId));
        assertTrue(stock.isEmpty() || stock.stream().allMatch(s -> s.getQuantity().signum() == 0));
        assertEquals("approved", purchaseOrderService.get(id).status());
    }

    /**
     * 用例 11:不允超收(rate=0)时 10.0001 拒绝。
     */
    @Test
    void noOverReceiptAtAll() {
        long id = createOrderWithRate("10", "0").id();
        purchaseOrderService.submit(id, "po_creator");
        purchaseOrderService.approve(id, "po_approver");
        long lineId = purchaseOrderService.get(id).items().get(0).id();
        assertThrows(BizException.class, () ->
                inboundService.create(new InboundCreateDTO(warehouseId, "超收",
                        List.of(new InboundLineDTO(itemId, new BigDecimal("10.0001"), null, null, null,
                                null, null, null, null, null, lineId)),
                        "purchase", id, LocalDate.now()), "po_creator"));
    }

    /**
     * 用例 12:未审批订单不允许到货。
     */
    @Test
    void arrivalOnDraftRejected() {
        long id = createOrder("10", "1", "13.00").id();
        long lineId = purchaseOrderService.get(id).items().get(0).id();
        assertThrows(BizException.class, () ->
                inboundService.create(new InboundCreateDTO(warehouseId, null,
                        List.of(new InboundLineDTO(itemId, new BigDecimal("1"), null, null, null,
                                null, null, null, null, null, lineId)),
                        "purchase", id, LocalDate.now()), "po_creator"));
    }

    /**
     * 用例 13:到货行不属于该订单 → 拒绝。
     */
    @Test
    void arrivalWithForeignLineRejected() {
        long idA = createOrder("10", "1", "13.00").id();
        long idB = createOrder("10", "1", "13.00").id();
        purchaseOrderService.submit(idA, "po_creator");
        purchaseOrderService.approve(idA, "po_approver");
        long foreignLine = purchaseOrderService.get(idB).items().get(0).id();
        assertThrows(BizException.class, () ->
                inboundService.create(new InboundCreateDTO(warehouseId, null,
                        List.of(new InboundLineDTO(itemId, new BigDecimal("1"), null, null, null,
                                null, null, null, null, null, foreignLine)),
                        "purchase", idA, LocalDate.now()), "po_creator"));
    }

    /**
     * 用例 14:物料快照——下单后改 Item.spec,历史订单行快照不变。
     */
    @Test
    void specSnapshotImmutable() {
        long id = createOrder("1", "10", "13.00").id();
        assertEquals("原规格", purchaseOrderService.get(id).items().get(0).specSnapshot());
        ItemDO item = itemMapper.selectById(itemId);
        item.setSpec("新规格");
        itemMapper.updateById(item);
        assertEquals("原规格", purchaseOrderService.get(id).items().get(0).specSnapshot());
    }

    /**
     * 用例 15:手工关闭 approved → closed;关闭后不可再操作(终态)。
     */
    @Test
    void manualCloseThenTerminal() {
        long id = createOrder("10", "1", "13.00").id();
        purchaseOrderService.submit(id, "po_creator");
        purchaseOrderService.approve(id, "po_approver");
        assertEquals("closed", purchaseOrderService.close(id, "po_approver").status());
        assertThrows(BizException.class, () -> purchaseOrderService.close(id, "po_approver"));
        assertThrows(BizException.class, () -> purchaseOrderService.voidDoc(id, "po_approver"));
        assertThrows(BizException.class, () -> purchaseOrderService.update(id,
                new PurchaseOrderCreateDTO(LocalDate.now(), supplierId, creatorUserId, null,
                        null, List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal("1"),
                                null, new BigDecimal("1"), null, null))), "po_creator"));
    }

    /**
     * 用例 16:作废后终态只读。
     */
    @Test
    void voidThenTerminal() {
        long id = createOrder("10", "1", "13.00").id();
        assertEquals("voided", purchaseOrderService.voidDoc(id, "po_approver").status());
        assertThrows(BizException.class, () -> purchaseOrderService.submit(id, "po_creator"));
        assertThrows(BizException.class, () -> purchaseOrderService.voidDoc(id, "po_approver"));
    }

    /**
     * 用例 17:completed 终态不可关闭/作废。
     */
    @Test
    void completedTerminal() {
        long id = createOrder("1", "1", "13.00").id();
        purchaseOrderService.submit(id, "po_creator");
        purchaseOrderService.approve(id, "po_approver");
        long lineId = purchaseOrderService.get(id).items().get(0).id();
        inboundService.create(new InboundCreateDTO(warehouseId, null,
                List.of(new InboundLineDTO(itemId, new BigDecimal("1"), null, null, null,
                        null, null, null, null, null, lineId)),
                "purchase", id, LocalDate.now()), "po_creator");
        assertEquals("completed", purchaseOrderService.get(id).status());
        assertThrows(BizException.class, () -> purchaseOrderService.close(id, "po_approver"));
        assertThrows(BizException.class, () -> purchaseOrderService.voidDoc(id, "po_approver"));
    }

    /**
     * 新建单行采购订单(默认不超收,税率 13)。
     *
     * @param qty   数量
     * @param price 单价
     * @param rate  税率
     * @return 订单 VO
     */
    private PurchaseOrderVO createOrder(String qty, String price, String rate) {
        return createOrderFull(qty, price, rate, "0");
    }

    /**
     * 新建单行采购订单(指定超收比例,单价 10,税率 13)。
     *
     * @param qty      数量
     * @param overRate 超收比例
     * @return 订单 VO
     */
    private PurchaseOrderVO createOrderWithRate(String qty, String overRate) {
        return createOrderFull(qty, "10", "13.00", overRate);
    }

    /**
     * 新建单行采购订单(全参数)。
     *
     * @param qty      数量
     * @param price    单价
     * @param rate     税率
     * @param overRate 超收比例
     * @return 订单 VO
     */
    private PurchaseOrderVO createOrderFull(String qty, String price, String rate, String overRate) {
        String code = nextCode();
        SupplierDO sp = new SupplierDO();
        sp.setSupplierCode(code + "-SP");
        sp.setSupplierName(code + "供应商");
        sp.setStatus(1);
        supplierMapper.insert(sp);
        return purchaseOrderService.create(new PurchaseOrderCreateDTO(LocalDate.now(),
                sp.getId(), creatorUserId, new BigDecimal(overRate), "测试",
                List.of(new PurchaseOrderLineDTO(itemId, new BigDecimal(qty), null,
                        new BigDecimal(price), new BigDecimal(rate), null))), "po_creator");
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

    /**
     * 唯一编码。
     *
     * @return 编码
     */
    private String nextCode() {
        return "PO" + (seq++);
    }
}
