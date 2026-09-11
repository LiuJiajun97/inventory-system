package com.company.inventory.stock;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.dto.stock.StockOpRequest;
import com.company.inventory.entity.stock.BatchDO;
import com.company.inventory.entity.item.ItemDO;
import com.company.inventory.entity.location.LocationDO;
import com.company.inventory.entity.stock.SerialDO;
import com.company.inventory.entity.stock.StockDO;
import com.company.inventory.entity.stock.StockTransactionDO;
import com.company.inventory.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.LocationMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.StockTransactionMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.vo.stock.StockLine;
import com.company.inventory.vo.stock.StockOpResult;









































import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;



















import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存核心单元测试(对照 server/test/stock-core.test.ts 的 9 条断言,业务规则零弱化)。
 * 每个用例独立造数据(唯一编码),类级别 TRUNCATE 测试库。
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
class StockCoreTest {

    /** 库存核心服务 */
    @Autowired
    private StockCoreService stockCoreService;
    /** JDBC 模板(执行 TRUNCATE) */
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 库位 Mapper */
    @Autowired
    private LocationMapper locationMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** 流水 Mapper */
    @Autowired
    private StockTransactionMapper txMapper;
    /** 批次 Mapper */
    @Autowired
    private BatchMapper batchMapper;
    /** 序列号 Mapper */
    @Autowired
    private SerialMapper serialMapper;

    /** 唯一编码种子(避免用例间互相干扰) */
    private long seq = 0;

    /**
     * 测试前置:清空测试库(与 TS 版 beforeAll 一致,外键顺序由 CASCADE 保证)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"OutboundDocItem\",\"InboundDocItem\",\"OutboundDoc\",\"InboundDoc\","
                + "\"StockTransaction\",\"Stock\",\"Serial\",\"Batch\",\"Location\",\"Item\","
                + "\"Warehouse\",\"User\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
    }

    /**
     * 用例 1:入库后 stock 行正确,流水 afterQty 正确。
     */
    @Test
    void inboundStockRowAndTx() {
        long[] env = setupWarehouse(false, false, false, false);
        StockOpResult result = inbound(env[0], line(env[1], 10, null, null, null, null), "RK-T1");
        assertEquals(0, BigDecimal.TEN.compareTo(result.getRows().get(0).qty()));

        StockDO stock = stock(env[0], env[1]);
        assertNotNull(stock);
        assertEquals(0, BigDecimal.TEN.compareTo(stock.getQuantity()));

        StockTransactionDO tx = findTx(env[0], env[1], "inbound");
        assertNotNull(tx);
        assertEquals(0, BigDecimal.TEN.compareTo(tx.getChangeQty()));
        assertEquals(0, BigDecimal.TEN.compareTo(tx.getAfterQty()));
    }

    /**
     * 用例 2:连续两次入库,第二次 afterQty = 第一次 + 增量。
     */
    @Test
    void consecutiveInbound() throws InterruptedException {
        long[] env = setupWarehouse(false, false, false, false);
        inbound(env[0], line(env[1], 7, null, null, null, null), "RK-T2a");
        Thread.sleep(20);
        inbound(env[0], line(env[1], 5, null, null, null, null), "RK-T2b");

        StockDO stock = stock(env[0], env[1]);
        assertEquals(0, new BigDecimal(12).compareTo(stock.getQuantity()));

        List<StockTransactionDO> txs = txMapper.selectList(new LambdaQueryWrapper<StockTransactionDO>()
                .eq(StockTransactionDO::getWarehouseId, env[0])
                .eq(StockTransactionDO::getItemId, env[1])
                .orderByAsc(StockTransactionDO::getId));
        assertEquals(2, txs.size());
        assertEquals(0, new BigDecimal(7).compareTo(txs.get(0).getAfterQty()));
        assertEquals(0, new BigDecimal(12).compareTo(txs.get(1).getAfterQty()));
    }

    /**
     * 用例 3:出库扣减正确,流水为负。
     */
    @Test
    void outboundDeducts() throws InterruptedException {
        long[] env = setupWarehouse(false, false, false, false);
        inbound(env[0], line(env[1], 20, null, null, null, null), "RK-T3");
        Thread.sleep(20);
        outbound(env[0], line(env[1], 7, null, null, null, null), "CK-T3");

        StockDO stock = stock(env[0], env[1]);
        assertEquals(0, new BigDecimal(13).compareTo(stock.getQuantity()));

        StockTransactionDO out = findTx(env[0], env[1], "outbound");
        assertNotNull(out);
        assertTrue(out.getChangeQty().signum() < 0);
        assertEquals(0, new BigDecimal(13).compareTo(out.getAfterQty()));
    }

    /**
     * 用例 4:出库数量大于库存,抛错且全部回滚。
     */
    @Test
    void outboundOverStockRollsBack() throws InterruptedException {
        long[] env = setupWarehouse(false, false, false, false);
        inbound(env[0], line(env[1], 5, null, null, null, null), "RK-T4");
        Thread.sleep(20);

        BizException ex = assertThrows(BizException.class,
                () -> outbound(env[0], line(env[1], 100, null, null, null, null), "CK-T4"));
        assertTrue(ex.getMessage().contains("库存不足"), "错误信息应含'库存不足': " + ex.getMessage());

        StockDO stock = stock(env[0], env[1]);
        assertEquals(0, BigDecimal.valueOf(5).compareTo(stock.getQuantity()));

        List<StockTransactionDO> txs = txMapper.selectList(new LambdaQueryWrapper<StockTransactionDO>()
                .eq(StockTransactionDO::getWarehouseId, env[0])
                .eq(StockTransactionDO::getItemId, env[1]));
        assertEquals(1, txs.size());
        assertEquals("inbound", txs.get(0).getBizCode());
    }

    /**
     * 用例 5:FEFO,同物品两批次,未指定批次出库扣到期早的。
     */
    @Test
    void fefoPicksEarlierExpiry() throws InterruptedException {
        long[] env = setupWarehouse(true, true, false, false);
        StockLine lineA = line(env[1], 10, "B-A", null, LocalDate.now().plusDays(60), null);
        StockLine lineB = line(env[1], 10, "B-B", null, LocalDate.now().plusDays(30), null);
        inbound(env[0], List.of(lineA, lineB), "RK-T5");
        Thread.sleep(20);

        StockOpResult out = outbound(env[0], line(env[1], 5, null, null, null, null), "CK-T5");
        BatchDO batchB = findBatch(env[1], "B-B");
        assertNotNull(batchB);
        assertEquals(batchB.getId(), out.getRows().get(0).batchId());

        StockDO stockB = stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, env[0])
                .eq(StockDO::getItemId, env[1])
                .eq(StockDO::getBatchId, batchB.getId()));
        assertEquals(0, BigDecimal.valueOf(5).compareTo(stockB.getQuantity()));
    }

    /**
     * 用例 6:FIFO,仅启用批次不启用保质期,先入的批次先出。
     */
    @Test
    void fifoPicksEarlierInbound() throws InterruptedException {
        long[] env = setupWarehouse(true, false, false, false);
        inbound(env[0], line(env[1], 10, "FIFO-A", null, null, null), "RK-T6a");
        Thread.sleep(60);
        inbound(env[0], line(env[1], 10, "FIFO-B", null, null, null), "RK-T6b");

        StockOpResult out = outbound(env[0], line(env[1], 5, null, null, null, null), "CK-T6");
        BatchDO batchA = findBatch(env[1], "FIFO-A");
        assertNotNull(batchA);
        assertEquals(batchA.getId(), out.getRows().get(0).batchId());

        StockDO stockA = stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, env[0])
                .eq(StockDO::getItemId, env[1])
                .eq(StockDO::getBatchId, batchA.getId()));
        assertEquals(0, BigDecimal.valueOf(5).compareTo(stockA.getQuantity()));
    }

    /**
     * 用例 7:序列号,入库 3 台(3 个序列号),出库 1 台,1 out 2 in_stock。
     */
    @Test
    void serialOutboundPartial() throws InterruptedException {
        long[] env = setupWarehouse(false, false, true, true);
        long locId = setupLocation(env[0]);
        inbound(env[0], line(env[1], 3, null, null, null,
                withLocation(locId, List.of("SN-001", "SN-002", "SN-003"))), "RK-T7");
        Thread.sleep(20);

        StockDO stock = stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, env[0])
                .eq(StockDO::getItemId, env[1])
                .eq(StockDO::getLocationId, locId));
        assertEquals(0, BigDecimal.valueOf(3).compareTo(stock.getQuantity()));

        outbound(env[0], line(env[1], 1, null, null, null,
                withLocation(locId, List.of("SN-002"))), "CK-T7");

        List<SerialDO> serials = serialMapper.selectList(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getItemId, env[1])
                .orderByAsc(SerialDO::getSerialNo));
        long outCount = serials.stream().filter(s -> "out".equals(s.getStatus())).count();
        long inCount = serials.stream().filter(s -> "in_stock".equals(s.getStatus())).count();
        assertEquals(1, outCount);
        assertEquals(2, inCount);
        assertTrue(serials.stream().anyMatch(s -> "SN-002".equals(s.getSerialNo())
                && "out".equals(s.getStatus())));
    }

    /**
     * 用例 8:序列号出库一个不存在的号,抛错且全部回滚。
     */
    @Test
    void serialNotExistsRollsBack() throws InterruptedException {
        long[] env = setupWarehouse(false, false, true, true);
        long locId = setupLocation(env[0]);
        inbound(env[0], line(env[1], 2, null, null, null,
                withLocation(locId, List.of("SN-A", "SN-B"))), "RK-T8");
        Thread.sleep(20);

        BizException ex = assertThrows(BizException.class,
                () -> outbound(env[0], line(env[1], 1, null, null, null,
                        withLocation(locId, List.of("SN-NOT-EXIST"))), "CK-T8"));
        assertTrue(ex.getMessage().contains("序列号"), "错误信息应含'序列号': " + ex.getMessage());

        StockDO stock = stock(env[0], env[1]);
        assertEquals(0, BigDecimal.valueOf(2).compareTo(stock.getQuantity()));

        List<StockTransactionDO> txs = txMapper.selectList(new LambdaQueryWrapper<StockTransactionDO>()
                .eq(StockTransactionDO::getWarehouseId, env[0])
                .eq(StockTransactionDO::getItemId, env[1]));
        assertEquals(1, txs.size());

        List<SerialDO> serials = serialMapper.selectList(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getItemId, env[1]));
        assertTrue(serials.stream().allMatch(s -> "in_stock".equals(s.getStatus())));
    }

    /**
     * 用例 9:配置校验,启用序列号的仓库出库不传 serialNos,抛错。
     */
    @Test
    void serialWarehouseOutboundWithoutSerials() throws InterruptedException {
        long[] env = setupWarehouse(false, false, true, true);
        long locId = setupLocation(env[0]);
        inbound(env[0], line(env[1], 2, null, null, null,
                withLocation(locId, List.of("SN-1", "SN-2"))), "RK-T9");
        Thread.sleep(20);

        StockLine outLine = line(env[1], 1, null, null, null, null);
        outLine.setLocationId(locId);
        BizException ex = assertThrows(BizException.class,
                () -> outbound(env[0], outLine, "CK-T9"));
        assertTrue(ex.getMessage().contains("序列号"), "错误信息应含'序列号': " + ex.getMessage());
    }

    /**
     * 造仓库 + 物品(唯一编码)。
     *
     * @param batch    启用批次
     * @param expiry   启用保质期
     * @param serial   启用序列号
     * @param location 启用库位
     * @return [warehouseId, itemId]
     */
    private long[] setupWarehouse(boolean batch, boolean expiry, boolean serial, boolean location) {
        seq++;
        String code = "T" + seq + "-" + System.nanoTime();
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName("测试仓" + seq);
        wh.setWarehouseType("raw");
        wh.setEnableBatch(batch);
        wh.setEnableExpiry(expiry);
        wh.setEnableSerial(serial);
        wh.setEnableLocation(location);
        warehouseMapper.insert(wh);
        ItemDO item = new ItemDO();
        item.setItemCode(code + "-IT");
        item.setItemName("测试物品" + seq);
        item.setUnit("件");
        itemMapper.insert(item);
        return new long[]{wh.getId(), item.getId()};
    }

    /**
     * 造库位。
     *
     * @param warehouseId 仓库 ID
     * @return 库位 ID
     */
    private long setupLocation(long warehouseId) {
        seq++;
        LocationDO loc = new LocationDO();
        loc.setWarehouseId(warehouseId);
        loc.setLocationCode("L-" + seq + "-" + System.nanoTime());
        loc.setLocationName("测试位");
        locationMapper.insert(loc);
        return loc.getId();
    }

    /**
     * 构造库存行。
     *
     * @param itemId        物品 ID
     * @param qty           数量
     * @param batchNo       批次号(可空)
     * @param productionDate 生产日期(可空)
     * @param expiryDate    到期日(可空)
     * @param extra         附加行(带库位/序列号,可空)
     * @return 库存行
     */
    private StockLine line(long itemId, int qty, String batchNo, LocalDate productionDate,
                           LocalDate expiryDate, StockLine extra) {
        StockLine sl = new StockLine();
        sl.setItemId(itemId);
        sl.setQty(BigDecimal.valueOf(qty));
        sl.setBatchNo(batchNo);
        sl.setProductionDate(productionDate);
        sl.setExpiryDate(expiryDate);
        if (extra != null) {
            sl.setLocationId(extra.getLocationId());
            sl.setSerialNos(extra.getSerialNos());
        }
        return sl;
    }

    /**
     * 构造仅带库位/序列号的占位行。
     *
     * @param locationId 库位 ID
     * @param serialNos  序列号列表
     * @return 占位行
     */
    private StockLine withLocation(long locationId, List<String> serialNos) {
        StockLine sl = new StockLine();
        sl.setLocationId(locationId);
        sl.setSerialNos(serialNos);
        return sl;
    }

    /**
     * 调用入库。
     *
     * @param warehouseId 仓库 ID
     * @param lines       行列表
     * @param docNo       单据号
     * @return 结果
     */
    private StockOpResult inbound(long warehouseId, List<StockLine> lines, String docNo) {
        StockOpRequest req = new StockOpRequest();
        req.setWarehouseId(warehouseId);
        req.setLines(lines);
        req.setDocNo(docNo);
        req.setOperator("tester");
        return stockCoreService.inbound(req);
    }

    /**
     * 调用入库(单行便捷方法)。
     *
     * @param warehouseId 仓库 ID
     * @param line        单行
     * @param docNo       单据号
     * @return 结果
     */
    private StockOpResult inbound(long warehouseId, StockLine line, String docNo) {
        return inbound(warehouseId, List.of(line), docNo);
    }

    /**
     * 调用出库(行列表)。
     *
     * @param warehouseId 仓库 ID
     * @param lines       行列表
     * @param docNo       单据号
     * @return 结果
     */
    private StockOpResult outbound(long warehouseId, List<StockLine> lines, String docNo) {
        StockOpRequest req = new StockOpRequest();
        req.setWarehouseId(warehouseId);
        req.setLines(lines);
        req.setDocNo(docNo);
        req.setOperator("tester");
        return stockCoreService.outbound(req);
    }

    /**
     * 调用出库(单行便捷方法)。
     *
     * @param warehouseId 仓库 ID
     * @param line        单行
     * @param docNo       单据号
     * @return 结果
     */
    private StockOpResult outbound(long warehouseId, StockLine line, String docNo) {
        return outbound(warehouseId, List.of(line), docNo);
    }

    /**
     * 查库存行。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @return 库存行(可空)
     */
    private StockDO stock(long warehouseId, long itemId) {
        return stockMapper.selectOne(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId)
                .eq(StockDO::getItemId, itemId)
                .last("LIMIT 1"));
    }

    /**
     * 查流水。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param bizCode     业务编码
     * @return 流水(可空)
     */
    private StockTransactionDO findTx(long warehouseId, long itemId, String bizCode) {
        return txMapper.selectOne(new LambdaQueryWrapper<StockTransactionDO>()
                .eq(StockTransactionDO::getWarehouseId, warehouseId)
                .eq(StockTransactionDO::getItemId, itemId)
                .eq(StockTransactionDO::getBizCode, bizCode)
                .last("LIMIT 1"));
    }

    /**
     * 查批次。
     *
     * @param itemId  物品 ID
     * @param batchNo 批次号
     * @return 批次(可空)
     */
    private BatchDO findBatch(long itemId, String batchNo) {
        return batchMapper.selectOne(new LambdaQueryWrapper<BatchDO>()
                .eq(BatchDO::getItemId, itemId)
                .eq(BatchDO::getBatchNo, batchNo));
    }
}
