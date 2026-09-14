package com.company.inventory.sales;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.outbound.OutboundCreateDTO;
import com.company.inventory.model.dto.outbound.OutboundLineDTO;
import com.company.inventory.model.dto.sales.SalesActionDTO;
import com.company.inventory.model.dto.sales.SalesOrderCreateDTO;
import com.company.inventory.model.dto.sales.SalesOrderLineDTO;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.model.vo.sales.SalesOrderVO;
import com.company.inventory.model.vo.stock.StockLine;

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
 * 销售订单测试:审批 FEFO 预占 + 发货扣预占 + 关闭/作废释放 + 超发拒绝 + 手工出库不击穿预占。
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
class SalesOrderTest {

    /** 销售订单服务 */
    @Autowired
    private SalesOrderService salesOrderService;
    /** 出库单服务(发货) */
    @Autowired
    private OutboundService outboundService;
    /** 库存核心服务 */
    @Autowired
    private StockCoreService stockCoreService;
    /** 客户 Mapper */
    @Autowired
    private CustomerMapper customerMapper;
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
    private SalesOrderMapper orderMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 制单人 */
    private long creatorUserId;
    /** 客户 */
    private long customerId;
    /** 物品 */
    private long itemId;
    /** 仓库 */
    private long warehouseId;
    /** 第二仓库(发货仓一致性校验用) */
    private long otherWarehouseId;

    /** 唯一编码种子 */
    private long seq = 0;

    /**
     * 每用例前置:库存重置为 100/预占 0(用例间隔离)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.update("UPDATE \"stock\" SET \"quantity\" = 100, \"pre_allocated_qty\" = 0 "
                + "WHERE \"warehouse_id\" = ? AND \"item_id\" = ?", warehouseId, itemId);
    }

    /**
     * 前置:清库并造基础数据(仓库/物品/客户/用户),入 100 件库存。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"outbound_doc_item\",\"inbound_doc_item\","
                + "\"purchase_order\",\"sales_order\",\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"outbound_doc\",\"inbound_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("SO-WH");
        wh.setWarehouseName("销售测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        WarehouseDO wh2 = new WarehouseDO();
        wh2.setWarehouseCode("SO-WH2");
        wh2.setWarehouseName("销售测试仓2");
        wh2.setWarehouseType("raw");
        wh2.setEnableBatch(false);
        wh2.setEnableExpiry(false);
        wh2.setEnableSerial(false);
        wh2.setEnableLocation(false);
        warehouseMapper.insert(wh2);
        otherWarehouseId = wh2.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("SO-IT");
        item.setItemName("销售测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();

        CustomerDO cu = new CustomerDO();
        cu.setCustomerCode("SO-CU");
        cu.setCustomerName("销售测试客户");
        cu.setStatus(1);
        customerMapper.insert(cu);
        customerId = cu.getId();

        UserDO user = new UserDO();
        user.setUsername("so_creator");
        user.setPasswordHash("$2a$10$dummy");
        user.setName("制单人");
        user.setRole("operator");
        user.setStatus(1);
        userMapper.insert(user);
        creatorUserId = user.getId();

        inbound(100);
    }

    /**
     * 用例 1:审批通过 → 预占落库,可用口径 = quantity - preAllocatedQty。
     */
    @Test
    void approvePreAllocates() {
        long id = createOrder("30");
        salesOrderService.submit(id, "so_creator");
        SalesOrderVO vo = salesOrderService.approve(id, "so_approver");
        assertEquals("approved", vo.status());
        StockDO stock = stock(itemId);
        assertEquals(0, new BigDecimal("100").compareTo(stock.getQuantity()));
        assertEquals(0, new BigDecimal("30").compareTo(stock.getPreAllocatedQty()));
        // 重复审批幂等
        assertEquals("approved", salesOrderService.approve(id, "so_approver").status());
    }

    /**
     * 用例 2:库存不足 → 审批失败,订单回 draft,预占整体回滚。
     */
    @Test
    void approveInsufficientStockBackToDraft() {
        long id = createOrder("200");
        salesOrderService.submit(id, "so_creator");
        BizException ex = assertThrows(BizException.class,
                () -> salesOrderService.approve(id, "so_approver"));
        assertTrue(ex.getMessage().contains("可用库存不足"));
        assertEquals("draft", salesOrderService.get(id).status());
        StockDO stock = stock(itemId);
        assertEquals(0, BigDecimal.ZERO.compareTo(
                stock.getPreAllocatedQty() == null ? BigDecimal.ZERO : stock.getPreAllocatedQty()));
    }

    /**
     * 用例 3:审批人=制单人 → 拒绝。
     */
    @Test
    void approveByCreatorRejected() {
        long id = createOrder("1");
        salesOrderService.submit(id, "so_creator");
        assertThrows(BizException.class, () -> salesOrderService.approve(id, "so_creator"));
    }

    /**
     * 用例 4:驳回必填原因。
     */
    @Test
    void rejectRequiresReason() {
        long id = createOrder("1");
        salesOrderService.submit(id, "so_creator");
        assertThrows(BizException.class, () ->
                salesOrderService.reject(id, new SalesActionDTO(null), "so_approver"));
        assertEquals("rejected", salesOrderService
                .reject(id, new SalesActionDTO("不要了"), "so_approver").status());
    }

    /**
     * 用例 5:整单发货 → 库存扣减 + 预占清零 + 订单自动 completed。
     */
    @Test
    void fullShipmentCompletesOrder() {
        long id = createOrder("30");
        salesOrderService.submit(id, "so_creator");
        salesOrderService.approve(id, "so_approver");
        long lineId = salesOrderService.get(id).items().get(0).id();

        outboundService.create(new OutboundCreateDTO(warehouseId, "销售发货",
                List.of(new OutboundLineDTO(itemId, new BigDecimal("30"), null, null,
                        null, null, null, null, lineId)),
                "sales", id, LocalDate.now()), "so_creator");

        SalesOrderVO vo = salesOrderService.get(id);
        assertEquals("completed", vo.status());
        assertEquals(0, new BigDecimal("30").compareTo(new BigDecimal(vo.items().get(0).shippedQty())));
        StockDO stock = stock(itemId);
        assertEquals(0, new BigDecimal("70").compareTo(stock.getQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stock.getPreAllocatedQty()));
    }

    /**
     * 用例 6:部分发货后超发拒绝(发货量 > 订单剩余量)。
     */
    @Test
    void overShipRejected() {
        long id = createOrder("10");
        salesOrderService.submit(id, "so_creator");
        salesOrderService.approve(id, "so_approver");
        long lineId = salesOrderService.get(id).items().get(0).id();
        outboundService.create(new OutboundCreateDTO(warehouseId, "部分发货",
                List.of(new OutboundLineDTO(itemId, new BigDecimal("4"), null, null,
                        null, null, null, null, lineId)),
                "sales", id, LocalDate.now()), "so_creator");
        assertThrows(BizException.class, () ->
                outboundService.create(new OutboundCreateDTO(warehouseId, "超发",
                        List.of(new OutboundLineDTO(itemId, new BigDecimal("7"), null, null,
                                null, null, null, null, lineId)),
                        "sales", id, LocalDate.now()), "so_creator"));
        // 库存 96(只扣了 4),预占剩 6
        StockDO stock = stock(itemId);
        assertEquals(0, new BigDecimal("96").compareTo(stock.getQuantity()));
        assertEquals(0, new BigDecimal("6").compareTo(stock.getPreAllocatedQty()));
    }

    /**
     * 用例 7:手工关闭 → 未发货部分预占释放。
     */
    @Test
    void closeReleasesPreAlloc() {
        long id = createOrder("10");
        salesOrderService.submit(id, "so_creator");
        salesOrderService.approve(id, "so_approver");
        long lineId = salesOrderService.get(id).items().get(0).id();
        outboundService.create(new OutboundCreateDTO(warehouseId, "部分发货",
                List.of(new OutboundLineDTO(itemId, new BigDecimal("3"), null, null,
                        null, null, null, null, lineId)),
                "sales", id, LocalDate.now()), "so_creator");
        assertEquals("closed", salesOrderService.close(id, "so_approver").status());
        StockDO stock = stock(itemId);
        assertEquals(0, new BigDecimal("97").compareTo(stock.getQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stock.getPreAllocatedQty()));
    }

    /**
     * 用例 8:作废 → 预占全部释放。
     */
    @Test
    void voidReleasesPreAlloc() {
        long id = createOrder("10");
        salesOrderService.submit(id, "so_creator");
        salesOrderService.approve(id, "so_approver");
        assertEquals("voided", salesOrderService.voidDoc(id, "so_approver").status());
        StockDO stock = stock(itemId);
        assertEquals(0, BigDecimal.ZERO.compareTo(
                stock.getPreAllocatedQty() == null ? BigDecimal.ZERO : stock.getPreAllocatedQty()));
        assertEquals(0, new BigDecimal("100").compareTo(stock.getQuantity()));
    }

    /**
     * 用例 9:未审批订单不允许发货。
     */
    @Test
    void shipOnDraftRejected() {
        long id = createOrder("1");
        long lineId = salesOrderService.get(id).items().get(0).id();
        assertThrows(BizException.class, () ->
                outboundService.create(new OutboundCreateDTO(warehouseId, null,
                        List.of(new OutboundLineDTO(itemId, new BigDecimal("1"), null, null,
                                null, null, null, null, lineId)),
                        "sales", id, LocalDate.now()), "so_creator"));
    }

    /**
     * 用例 10:出库仓库与订单发货仓不一致 → 拒绝。
     */
    @Test
    void shipWarehouseMismatchRejected() {
        long id = createOrder("1");
        long lineId = salesOrderService.get(id).items().get(0).id();
        salesOrderService.submit(id, "so_creator");
        salesOrderService.approve(id, "so_approver");
        assertThrows(BizException.class, () ->
                outboundService.create(new OutboundCreateDTO(otherWarehouseId, null,
                        List.of(new OutboundLineDTO(itemId, new BigDecimal("1"), null, null,
                                null, null, null, null, lineId)),
                        "sales", id, LocalDate.now()), "so_creator"));
    }

    /**
     * 用例 11:手工出库不击穿他人预占(拍板点 2)。
     */
    @Test
    void manualOutboundCannotBreakPreAlloc() {
        long id = createOrder("100");
        salesOrderService.submit(id, "so_creator");
        salesOrderService.approve(id, "so_approver");
        // 可用量 = 0,手工出库 1 件必须失败
        assertThrows(BizException.class, () ->
                outboundService.create(new OutboundCreateDTO(warehouseId, "手工出库",
                        List.of(new OutboundLineDTO(itemId, new BigDecimal("1"), null, null,
                                null, null, null, null, null)),
                        null, null, null), "so_creator"));
    }

    /**
     * 用例 12:completed 终态不可关闭/作废。
     */
    @Test
    void completedTerminal() {
        long id = createOrder("5");
        salesOrderService.submit(id, "so_creator");
        salesOrderService.approve(id, "so_approver");
        long lineId = salesOrderService.get(id).items().get(0).id();
        outboundService.create(new OutboundCreateDTO(warehouseId, null,
                List.of(new OutboundLineDTO(itemId, new BigDecimal("5"), null, null,
                        null, null, null, null, lineId)),
                "sales", id, LocalDate.now()), "so_creator");
        assertEquals("completed", salesOrderService.get(id).status());
        assertThrows(BizException.class, () -> salesOrderService.close(id, "so_approver"));
        assertThrows(BizException.class, () -> salesOrderService.voidDoc(id, "so_approver"));
    }

    /**
     * 新建单行销售订单并返回 ID。
     *
     * @param qty 订购数量
     * @return 订单 ID
     */
    private long createOrder(String qty) {
        CustomerDO cu = new CustomerDO();
        cu.setCustomerCode("SO-CU" + (seq++));
        cu.setCustomerName("临时客户");
        cu.setStatus(1);
        customerMapper.insert(cu);
        return salesOrderService.create(new SalesOrderCreateDTO(LocalDate.now(), cu.getId(),
                creatorUserId, warehouseId, null,
                List.of(new SalesOrderLineDTO(itemId, new BigDecimal(qty), null,
                        new BigDecimal("20"), null, new BigDecimal("13.00"), null))), "so_creator").id();
    }

    /**
     * 手工入库 N 件。
     *
     * @param qty 数量
     */
    private void inbound(int qty) {
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(warehouseId);
        StockLine line = new StockLine();
        line.setItemId(itemId);
        line.setQty(new BigDecimal(qty));
        request.setLines(List.of(line));
        request.setDocNo("RK-TEST");
        request.setOperator("so_test");
        stockCoreService.inbound(request);
    }

    /**
     * 查该物品库存行。
     *
     * @param itemId 物品 ID
     * @return 库存行
     */
    private StockDO stock(long itemId) {
        StockDO stock = stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .eq(StockDO::getItemId, itemId));
        assertNotNull(stock);
        return stock;
    }
}
