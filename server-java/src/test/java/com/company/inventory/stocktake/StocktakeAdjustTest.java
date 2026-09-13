package com.company.inventory.stocktake;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.model.dto.adjust.StockAdjustCreateDTO;
import com.company.inventory.model.dto.adjust.StockAdjustLineDTO;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.dto.stocktake.StocktakeActualDTO;
import com.company.inventory.model.dto.stocktake.StocktakeActualLineDTO;
import com.company.inventory.model.dto.stocktake.StocktakeCreateDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.stock.StockTransactionDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.StockTransactionMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.StockAdjustService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.service.StocktakeService;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.model.vo.stocktake.StocktakeDocVO;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 盘点+库存调整测试:快照 bookQty + 实盘 + 刷新 + 差异生成调整单(盘盈/盘亏各一张)+ 调整执行改库存。
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
class StocktakeAdjustTest {

    /** 盘点服务 */
    @Autowired
    private StocktakeService stocktakeService;
    /** 调整服务 */
    @Autowired
    private StockAdjustService stockAdjustService;
    /** 库存核心服务 */
    @Autowired
    private StockCoreService stockCoreService;
    /** 物品 Mapper */
    @Autowired
    private ItemMapper itemMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** 库存 Mapper */
    @Autowired
    private StockMapper stockMapper;
    /** 流水 Mapper */
    @Autowired
    private StockTransactionMapper txMapper;
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 物品 A(盘盈场景) */
    private long itemA;
    /** 物品 B(盘亏场景) */
    private long itemB;
    /** 仓库 */
    private long warehouseId;

    /**
     * 前置:清库,两物品各入 10 件。
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
        wh.setWarehouseCode("PD-WH");
        wh.setWarehouseName("盘点测试仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        warehouseId = wh.getId();
        itemA = insertItem("PD-A");
        itemB = insertItem("PD-B");
        inbound(itemA, 10);
        inbound(itemB, 10);
    }

    /**
     * 每用例前置:两物品库存重置为 10(用例间隔离)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.update("UPDATE \"stock\" SET \"quantity\" = 10 WHERE \"warehouse_id\" = ?", warehouseId);
        jdbcTemplate.execute("DELETE FROM \"stock_adjust_doc\"");
        jdbcTemplate.execute("DELETE FROM \"stock_adjust_doc_item\"");
        jdbcTemplate.execute("DELETE FROM \"stocktake_doc\"");
        jdbcTemplate.execute("DELETE FROM \"stocktake_doc_item\"");
    }

    /**
     * 用例 1:新建盘点单 → bookQty 快照等于当时余额。
     */
    @Test
    void createSnapshotsBookQty() {
        StocktakeDocVO vo = stocktakeService.create(
                new StocktakeCreateDTO(warehouseId, LocalDate.now(), "all", null, "全仓盘"), "pd_creator");
        assertEquals("draft", vo.status());
        assertTrue(vo.items().size() >= 2);
        for (var line : vo.items()) {
            assertEquals(0, new BigDecimal("10").compareTo(new BigDecimal(line.bookQty())));
            // 未录实盘时差异为空
            assertNull(line.diffQty());
        }
    }

    /**
     * 用例 2:录入实盘 → diffQty 重算;刷新快照后 bookQty 跟随库存变化。
     */
    @Test
    void enterActualAndRefreshBook() {
        StocktakeDocVO vo = stocktakeService.create(
                new StocktakeCreateDTO(warehouseId, LocalDate.now(), "all", null, null), "pd_creator");
        long lineId = vo.items().stream().filter(l -> l.itemId().equals(itemA)).findFirst().orElseThrow().id();

        stocktakeService.enterActual(vo.id(), new StocktakeActualDTO(
                List.of(new StocktakeActualLineDTO(lineId, new BigDecimal("12")))), "pd_creator");
        var after = stocktakeService.get(vo.id());
        var line = after.items().stream().filter(l -> l.id().equals(lineId)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("12").compareTo(new BigDecimal(line.actualQty())));
        assertEquals(0, new BigDecimal("2").compareTo(new BigDecimal(line.diffQty())));

        // 库存被手工加 5,刷新快照后 bookQty = 15
        inbound(itemA, 5);
        StocktakeDocVO refreshed = stocktakeService.refreshBook(vo.id(), "pd_creator");
        line = refreshed.items().stream().filter(l -> l.id().equals(lineId)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("15").compareTo(new BigDecimal(line.bookQty())));
        assertEquals(0, new BigDecimal("-3").compareTo(new BigDecimal(line.diffQty())));
        // 已录实盘不被覆盖
        assertEquals(0, new BigDecimal("12").compareTo(new BigDecimal(line.actualQty())));
    }

    /**
     * 用例 3:无差异 → 生成调整单拒绝(无差异行)。
     */
    @Test
    void noDiffNoAdjust() {
        long id = stocktakeService.create(
                new StocktakeCreateDTO(warehouseId, LocalDate.now(), "all", null, null), "pd_creator").id();
        assertThrows(BizException.class, () -> stocktakeService.generateAdjust(id, "pd_creator"));
    }

    /**
     * 用例 4:盘盈+盘亏 → 生成 2 张调整单,refDocNo=盘点单号;调整后库存 = 实盘数。
     */
    @Test
    void generateAdjustAndExecute() {
        StocktakeDocVO vo = stocktakeService.create(
                new StocktakeCreateDTO(warehouseId, LocalDate.now(), "all", null, null), "pd_creator");
        StocktakeDocVO got = stocktakeService.get(vo.id());
        long lineA = got.items().stream().filter(l -> l.itemId().equals(itemA)).findFirst().orElseThrow().id();
        long lineB = got.items().stream().filter(l -> l.itemId().equals(itemB)).findFirst().orElseThrow().id();
        // A 盘盈 +2,B 盘亏 -3
        stocktakeService.enterActual(vo.id(), new StocktakeActualDTO(
                List.of(new StocktakeActualLineDTO(lineA, new BigDecimal("12")),
                        new StocktakeActualLineDTO(lineB, new BigDecimal("7")))), "pd_creator");

        List<StockAdjustDocVO> docs = stocktakeService.generateAdjust(vo.id(), "pd_creator");
        assertEquals(2, docs.size());
        StockAdjustDocVO gain = docs.stream().filter(d -> "gain".equals(d.adjustType())).findFirst().orElseThrow();
        StockAdjustDocVO loss = docs.stream().filter(d -> "loss".equals(d.adjustType())).findFirst().orElseThrow();
        assertEquals(vo.docNo(), gain.refDocNo());
        assertEquals(vo.docNo(), loss.refDocNo());

        // 审批执行
        stockAdjustService.submit(gain.id(), "pd_creator");
        stockAdjustService.approve(gain.id(), "pd_approver");
        stockAdjustService.submit(loss.id(), "pd_creator");
        stockAdjustService.approve(loss.id(), "pd_approver");

        assertEquals(0, new BigDecimal("12").compareTo(stockQty(itemA)));
        assertEquals(0, new BigDecimal("7").compareTo(stockQty(itemB)));
        // 流水类型
        assertEquals(1, txCount("adjust_in"));
        assertEquals(1, txCount("adjust_out"));
        // 防重复生成:盘点单已有未作废调整单,再次生成必须拒绝(防盘亏被重复扣减)
        assertThrows(BizException.class, () -> stocktakeService.generateAdjust(vo.id(), "pd_creator"));
        // 盘点单回读带已生成标记
        assertTrue(stocktakeService.get(vo.id()).adjustGenerated());
    }

    /**
     * 用例 4b:防重复生成的放行与拒绝边界(全部作废后可重新生成;部分作废仍拒绝;
     * 手工建单 refDocNo 被服务端忽略不占索引位;列表回读也带已生成标记)。
     */
    @Test
    void generateAdjustIdempotencyBoundaries() {
        StocktakeDocVO vo = stocktakeService.create(
                new StocktakeCreateDTO(warehouseId, LocalDate.now(), "all", null, null), "pd_creator");
        long id = vo.id();
        // 无差异行直接生成 → 400
        assertThrows(BizException.class, () -> stocktakeService.generateAdjust(id, "pd_creator"));
        // 录入实盘(B 盘亏 -3)后生成 loss 单
        StocktakeDocVO got = stocktakeService.get(id);
        long lineB = got.items().stream().filter(l -> l.itemId().equals(itemB)).findFirst().orElseThrow().id();
        stocktakeService.enterActual(id, new StocktakeActualDTO(
                List.of(new StocktakeActualLineDTO(lineB, new BigDecimal("7")))), "pd_creator");
        List<StockAdjustDocVO> first = stocktakeService.generateAdjust(id, "pd_creator");
        assertEquals(1, first.size());
        StockAdjustDocVO loss = first.get(0);
        // 部分作废口径:仍有未作废调整单时拒绝
        assertThrows(BizException.class, () -> stocktakeService.generateAdjust(id, "pd_creator"));
        // 手工建单传 refDocNo 被忽略,不占索引位(防手工单污染盘点单防重约束)
        StockAdjustDocVO manual = stockAdjustService.create(new StockAdjustCreateDTO(warehouseId,
                LocalDate.now(), "gain", vo.docNo(), "手工",
                List.of(new StockAdjustLineDTO(itemA, new BigDecimal("1"), new BigDecimal("1"),
                        null, null, null))), "pd_creator");
        assertNull(manual.refDocNo());
        // 全部作废后可重新生成,新单 ref 相同
        stockAdjustService.voidDoc(loss.id(), "pd_creator");
        List<StockAdjustDocVO> regen = stocktakeService.generateAdjust(id, "pd_creator");
        assertEquals(1, regen.size());
        assertEquals(vo.docNo(), regen.get(0).refDocNo());
    }

    /**
     * 用例 5:手工报废调整单(approve 执行 + 终态只读 + 类型校验)。
     */
    @Test
    void manualScrapAdjust() {
        StockAdjustDocVO vo = stockAdjustService.create(new StockAdjustCreateDTO(warehouseId,
                LocalDate.now(), "loss", null, "报废",
                List.of(new StockAdjustLineDTO(itemA, new BigDecimal("4"), new BigDecimal("1"),
                        null, null, "破损"))), "pd_creator");
        stockAdjustService.submit(vo.id(), "pd_creator");
        StockAdjustDocVO done = stockAdjustService.approve(vo.id(), "pd_approver");
        assertEquals("completed", done.status());
        assertEquals(0, new BigDecimal("6").compareTo(stockQty(itemA)));
        // 重复审批幂等,终态不可作废
        assertEquals("completed", stockAdjustService.approve(vo.id(), "pd_approver").status());
        assertThrows(BizException.class, () -> stockAdjustService.voidDoc(vo.id(), "pd_creator"));
    }

    /**
     * 用例 6:非法调整类型 → 拒绝。
     */
    @Test
    void invalidAdjustTypeRejected() {
        assertThrows(BizException.class, () -> stockAdjustService.create(new StockAdjustCreateDTO(warehouseId,
                LocalDate.now(), "magic", null, null,
                List.of(new StockAdjustLineDTO(itemA, new BigDecimal("1"), new BigDecimal("1"),
                        null, null, null))), "pd_creator"));
    }

    /**
     * 用例 7:盘点单驳回必填原因;终态只读。
     */
    @Test
    void stocktakeStateMachine() {
        long id = stocktakeService.create(
                new StocktakeCreateDTO(warehouseId, LocalDate.now(), "all", null, null), "pd_creator").id();
        stocktakeService.submit(id, "pd_creator");
        assertThrows(BizException.class, () -> stocktakeService.reject(id, "  ", "pd_approver"));
        assertEquals("rejected", stocktakeService.reject(id, "账不符", "pd_approver").status());
        // rejected 不可再审批,作废后终态
        assertThrows(BizException.class, () -> stocktakeService.approve(id, "pd_approver"));
        stocktakeService.voidDoc(id, "pd_approver");
        assertThrows(BizException.class, () -> stocktakeService.voidDoc(id, "pd_approver"));
    }

    /**
     * 用例 4c:序列号仓库盘点差异调整(盘盈自动生成台账序列号,盘亏按台账选取,台账不足拒绝)。
     */
    @Test
    void serialWarehouseStocktakeAdjust() {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("PD-SN");
        wh.setWarehouseName("序列号测试仓");
        wh.setWarehouseType("finished");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(true);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        long itemId = insertItem("PD-SN-ITEM");
        // 序列号仓入库 2 件(逐个 SN)
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(wh.getId());
        request.setDocNo("RK-PD-SN");
        request.setOperator("pd_test");
        StockLine line = new StockLine();
        line.setItemId(itemId);
        line.setQty(new BigDecimal("2"));
        line.setSerialNos(List.of("SN-PD-1001", "SN-PD-1002"));
        request.setLines(List.of(line));
        stockCoreService.inbound(request);
        // 盘盈 +1:生成 gain 调整单并审批执行,台账新增 1 个自动序列号
        StocktakeDocVO vo = stocktakeService.create(new StocktakeCreateDTO(
                wh.getId(), LocalDate.now(), "item", List.of(itemId), null), "pd_creator");
        StocktakeDocVO got = stocktakeService.get(vo.id());
        long lineId = got.items().get(0).id();
        stocktakeService.enterActual(vo.id(), new StocktakeActualDTO(
                List.of(new StocktakeActualLineDTO(lineId, new BigDecimal("3")))), "pd_creator");
        List<StockAdjustDocVO> docs = stocktakeService.generateAdjust(vo.id(), "pd_creator");
        assertEquals(1, docs.size());
        StockAdjustDocVO gain = docs.get(0);
        stockAdjustService.submit(gain.id(), "pd_creator");
        StockAdjustDocVO done = stockAdjustService.approve(gain.id(), "pd_approver");
        assertEquals("completed", done.status());
        assertEquals(0, new BigDecimal("3").compareTo(serialStockQty(wh.getId(), itemId)));
        Long serialCount = jdbcTemplate.queryForObject(
                "select count(*) from serial where item_id = ? and status = 'in_stock'",
                Long.class, itemId);
        assertEquals(3L, serialCount);
        // 盘亏 -1:按台账选取执行
        StocktakeDocVO vo2 = stocktakeService.create(new StocktakeCreateDTO(
                wh.getId(), LocalDate.now(), "item", List.of(itemId), null), "pd_creator");
        long lineId2 = stocktakeService.get(vo2.id()).items().get(0).id();
        stocktakeService.enterActual(vo2.id(), new StocktakeActualDTO(
                List.of(new StocktakeActualLineDTO(lineId2, new BigDecimal("2")))), "pd_creator");
        StockAdjustDocVO loss = stocktakeService.generateAdjust(vo2.id(), "pd_creator").get(0);
        stockAdjustService.submit(loss.id(), "pd_creator");
        assertEquals("completed", stockAdjustService.approve(loss.id(), "pd_approver").status());
        assertEquals(0, new BigDecimal("2").compareTo(serialStockQty(wh.getId(), itemId)));
        // 台账不足:手工调整单盘亏 3 个(台账仅 2 个),审批必须拒绝(不静默扣)
        StockAdjustDocVO overLoss = stockAdjustService.create(new StockAdjustCreateDTO(wh.getId(),
                LocalDate.now(), "loss", null, null,
                List.of(new StockAdjustLineDTO(itemId, new BigDecimal("3"), new BigDecimal("1"),
                        null, null, "台账不足场景"))), "pd_creator");
        stockAdjustService.submit(overLoss.id(), "pd_creator");
        assertThrows(BizException.class, () -> stockAdjustService.approve(overLoss.id(), "pd_approver"));
        // 清理本用例残留(adjust 流水会污染其他用例的全表计数)
        jdbcTemplate.update("DELETE FROM stock_transaction WHERE item_id = ?", itemId);
        jdbcTemplate.update("DELETE FROM serial WHERE item_id = ?", itemId);
    }

    /**
     * 序列号仓物品库存总量。
     *
     * @param whId   仓库 ID
     * @param itemId 物品 ID
     * @return 总量
     */
    private BigDecimal serialStockQty(long whId, long itemId) {
        List<StockDO> rows = stockMapper.selectList(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, whId).eq(StockDO::getItemId, itemId));
        BigDecimal total = BigDecimal.ZERO;
        for (StockDO row : rows) {
            total = total.add(row.getQuantity());
        }
        return total;
    }

    /**
     * 造物品。
     *
     * @param code 编码
     * @return 物品 ID
     */
    private long insertItem(String code) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(code + "名称");
        item.setUnit("件");
        itemMapper.insert(item);
        return item.getId();
    }

    /**
     * 手工入库。
     *
     * @param itemId 物品 ID
     * @param qty 数量
     */
    private void inbound(long itemId, int qty) {
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(warehouseId);
        StockLine line = new StockLine();
        line.setItemId(itemId);
        line.setQty(new BigDecimal(qty));
        request.setLines(List.of(line));
        request.setDocNo("RK-PD");
        request.setOperator("pd_test");
        stockCoreService.inbound(request);
    }

    /**
     * 物品库存总量。
     *
     * @param itemId 物品 ID
     * @return 总量
     */
    private BigDecimal stockQty(long itemId) {
        List<StockDO> rows = stockMapper.selectList(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, warehouseId).eq(StockDO::getItemId, itemId));
        BigDecimal total = BigDecimal.ZERO;
        for (StockDO row : rows) {
            total = total.add(row.getQuantity());
        }
        return total;
    }

    /**
     * 流水计数。
     *
     * @param bizCode 业务类型
     * @return 条数
     */
    private long txCount(String bizCode) {
        Long count = txMapper.selectCount(new LambdaQueryWrapper<StockTransactionDO>()
                .eq(StockTransactionDO::getBizCode, bizCode));
        return count == null ? 0 : count;
    }
}
