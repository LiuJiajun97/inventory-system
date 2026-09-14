package com.company.inventory.stock;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.purchase.ArrivalLine;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderLineDTO;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.stock.StockTransactionDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.StockTransactionMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;
import com.company.inventory.model.vo.stock.StockLine;



























import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;












import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发穿仓测试(对照 server/test/concurrency.test.ts):
 * 同一 (warehouse,item) 入 10 件,20 并发各出 1 件,恰好 10 成功 10 失败,
 * 最终库存 0、出库流水 10 条、最后一条 afterQty=0、失败信息含"库存不足"。
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
class ConcurrencyTest {

    /** 库存核心服务 */
    @Autowired
    private StockCoreService stockCoreService;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** 流水 Mapper */
    @Autowired
    private StockTransactionMapper txMapper;
    /** JDBC 模板 */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;
    /** 供应商 Mapper */
    @Autowired
    private SupplierMapper supplierMapper;
    /** 采购订单服务 */
    @Autowired
    private PurchaseOrderService purchaseOrderService;

    /** 测试仓库 ID */
    private long warehouseId;
    /** 测试物品 ID */
    private long itemId;

    /**
     * 前置:清库并造并发测试数据(10 件库存)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\"," +
                "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"outbound_doc_item\",\"inbound_doc_item\"," +
                "\"purchase_order\",\"sales_order\",\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\"," +
                "\"outbound_doc\",\"inbound_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\"," +
                "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("CONC");
        wh.setWarehouseName("并发测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();

        ItemDO item = new ItemDO();
        item.setItemCode("CONC-IT");
        item.setItemName("并发测试物");
        item.setUnit("件");
        itemMapper.insert(item);
        itemId = item.getId();

        StockOpRequest req = new StockOpRequest();
        req.setWarehouseId(warehouseId);
        req.setLines(List.of(buildLine(10)));
        req.setDocNo("RK-CONC");
        req.setOperator("tester");
        stockCoreService.inbound(req);
    }

    /**
     * 每用例后置:库存重置为 10/预占 0,清流水(保证各并发用例独立)。
     */
    @AfterEach
    void resetAfterEach() {
        jdbcTemplate.update("UPDATE \"stock\" SET \"quantity\" = 10, \"pre_allocated_qty\" = 0 "
                + "WHERE \"warehouse_id\" = ? AND \"item_id\" = ?", warehouseId, itemId);
        jdbcTemplate.execute("DELETE FROM \"stock_transaction\"");
    }

    /**
     * 20 并发出库 1 件,库存 10,恰好 10 成功 10 失败。
     *
     * @throws Exception 并发执行异常
     */
    @Test
    void twentyConcurrentOutbound() throws Exception {
        StockDO before = stockLine();
        assertNotNull(before);
        assertEquals(0, BigDecimal.TEN.compareTo(before.getQuantity()));

        int total = 20;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            int idx = i;
            tasks.add(() -> {
                try {
                    StockOpRequest req = new StockOpRequest();
                    req.setWarehouseId(warehouseId);
                    req.setLines(List.of(buildLine(1)));
                    req.setDocNo(String.format("CK-CONC-%02d", idx + 1));
                    req.setOperator("tester");
                    stockCoreService.outbound(req);
                    return "ok";
                } catch (BizException e) {
                    return e.getMessage();
                }
            });
        }
        List<Future<String>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        int okCount = 0;
        List<String> failMessages = new ArrayList<>();
        for (Future<String> f : futures) {
            String r = f.get();
            if ("ok".equals(r)) {
                okCount++;
            } else {
                failMessages.add(r);
            }
        }
        assertEquals(10, okCount);
        assertEquals(10, failMessages.size());

        StockDO after = stockLine();
        assertEquals(0, BigDecimal.ZERO.compareTo(after.getQuantity()));

        List<StockTransactionDO> outTxs = txMapper.selectList(
                new LambdaQueryWrapper<StockTransactionDO>()
                        .eq(StockTransactionDO::getWarehouseId, warehouseId)
                        .eq(StockTransactionDO::getItemId, itemId)
                        .eq(StockTransactionDO::getBizCode, "outbound"));
        assertEquals(10, outTxs.size());

        StockTransactionDO last = outTxs.stream()
                .max(Comparator.comparing(StockTransactionDO::getCreatedAt)
                        .thenComparing(StockTransactionDO::getId))
                .orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(last.getAfterQty()));

        for (String msg : failMessages) {
            assertTrue(msg.contains("库存不足"), "失败信息应含'库存不足': " + msg);
        }
    }

    /**
     * 构造单行出库请求。
     *
     * @param qty 数量
     * @return 库存行
     */
    private StockLine buildLine(int qty) {
        StockLine sl = new StockLine();
        sl.setItemId(itemId);
        sl.setQty(BigDecimal.valueOf(qty));
        return sl;
    }

    /**
     * 查测试物品库存行。
     *
     * @return 库存行
     */
    private StockDO stockLine() {
        return stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .eq(StockDO::getItemId, itemId)
                .last("LIMIT 1"));
    }

    /**
     * 并发预占:库存 10,20 线程各预占 1,恰好 10 成功,预占总量 = 10。
     *
     * @throws Exception 并发执行异常
     */
    @Test
    void twentyConcurrentPreAlloc() throws Exception {
        int total = 20;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            tasks.add(() -> {
                try {
                    stockCoreService.preAlloc(warehouseId, itemId, BigDecimal.ONE, "并发预占测试");
                    return "ok";
                } catch (BizException e) {
                    return e.getMessage();
                }
            });
        }
        List<Future<String>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        int okCount = 0;
        for (Future<String> f : futures) {
            if ("ok".equals(f.get())) {
                okCount++;
            }
        }
        assertEquals(10, okCount);
        StockDO stock = stockLine();
        assertEquals(0, BigDecimal.TEN.compareTo(stock.getPreAllocatedQty()));
        List<StockTransactionDO> txs = txMapper.selectList(
                new LambdaQueryWrapper<StockTransactionDO>()
                        .eq(StockTransactionDO::getWarehouseId, warehouseId)
                        .eq(StockTransactionDO::getItemId, itemId)
                        .eq(StockTransactionDO::getBizCode, "pre_alloc"));
        assertEquals(10, txs.size());
    }

    /**
     * 并发到货回写:订单 10 件不允超收,20 线程各回写 1,恰好 10 成功,到货量 = 10。
     *
     * @throws Exception 并发执行异常
     */
    @Test
    void twentyConcurrentArrival() throws Exception {
        UserDO user = new UserDO();
        user.setUsername("conc_creator");
        user.setPasswordHash("$2a$10$dummy");
        user.setName("并发制单人");
        user.setRole("operator");
        user.setStatus(1);
        userMapper.insert(user);
        SupplierDO sp = new SupplierDO();
        sp.setSupplierCode("CONC-SP");
        sp.setSupplierName("并发测试供应商");
        sp.setStatus(1);
        supplierMapper.insert(sp);
        PurchaseOrderVO po = purchaseOrderService.create(new PurchaseOrderCreateDTO(LocalDate.now(),
                sp.getId(), user.getId(), BigDecimal.ZERO, null,
                List.of(new PurchaseOrderLineDTO(itemId, BigDecimal.TEN, null,
                        BigDecimal.ONE, null, new BigDecimal("13.00"), null))), "conc_creator");
        purchaseOrderService.submit(po.id(), "conc_creator");
        purchaseOrderService.approve(po.id(), "conc_approver");
        long lineId = purchaseOrderService.get(po.id()).items().get(0).id();

        int total = 20;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            tasks.add(() -> {
                try {
                    purchaseOrderService.applyArrival(po.id(),
                            List.of(new ArrivalLine(lineId, BigDecimal.ONE)));
                    return "ok";
                } catch (BizException e) {
                    return e.getMessage();
                }
            });
        }
        List<Future<String>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        int okCount = 0;
        for (Future<String> f : futures) {
            if ("ok".equals(f.get())) {
                okCount++;
            }
        }
        assertEquals(10, okCount);
        assertEquals(0, BigDecimal.TEN.compareTo(new BigDecimal(
                purchaseOrderService.get(po.id()).items().get(0).arrivedQty())));
    }
}
