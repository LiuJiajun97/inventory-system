package com.company.inventory.report;

import com.company.inventory.common.support.DataScope;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.report.CostReportQuery;
import com.company.inventory.model.vo.report.CostReportRow;
import com.company.inventory.model.vo.report.CostReportVO;
import com.company.inventory.service.CostReportService;

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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存成本(移动均价)报表测试:零建表实时回放口径验证。
 *
 * <p>覆盖:加权入库/出库消耗(售价无关)/二次入库重算均价/调拨成本随货走/盘亏盘盈均价不变/
 * 期初首条权重与缺价按 0/数量对账(afterQty 权威)/DataScope 口径/date 截止回放。</p>
 *
 * <p>自造数据(JdbcTemplate 直接落库,时间可控)自清(每用例前置清空单据/库存/流水)。</p>
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
class CostReportTest {

    @Autowired
    private ItemMapper itemMapper;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CostReportService costReportService;

    /** 仓 1 */
    private long wh1;
    /** 仓 2 */
    private long wh2;
    /** 物品 A */
    private long itemA;
    /** 物品 B */
    private long itemB;
    /** 物品 C(缺价用例) */
    private long itemC;

    /**
     * 前置:清库并造主数据(两仓三物)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"inbound_doc_item\",\"inbound_doc\",\"outbound_doc_item\",\"outbound_doc\","
                + "\"purchase_return_item\",\"sales_return_item\",\"purchase_return\",\"sales_return\","
                + "\"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\",\"transfer_doc\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"purchase_order\",\"sales_order\","
                + "\"stocktake_doc\",\"stock_adjust_doc\",\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        wh1 = insertWarehouse("COST-WH1");
        wh2 = insertWarehouse("COST-WH2");
        itemA = insertItem("COST-A");
        itemB = insertItem("COST-B");
        itemC = insertItem("COST-C");
    }

    /**
     * 每用例前置:清空单据/库存/流水/批次(用例间隔离,主数据保留)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.execute("TRUNCATE \"inbound_doc_item\",\"inbound_doc\","
                + "\"outbound_doc_item\",\"outbound_doc\","
                + "\"transfer_doc_item\",\"transfer_doc\","
                + "\"stock_transaction\",\"stock\",\"batch\"");
    }

    /**
     * 用例 1:手工入库 10@2.00 → 均价 2.00,金额 20。
     */
    @Test
    void inboundSetsWeightedAvg() {
        String doc = insertInboundDoc(wh1, itemA, "COST-IN-1", "10", "2.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "10", "inbound", doc, at(10));
        CostReportRow row = rowOf(allRows(), wh1, itemA, 0L);
        assertEquals("10", row.quantity());
        assertEquals("2.0000", row.avgPrice());
        assertEquals("20.00", row.amount());
    }

    /**
     * 用例 2:出库 4 → 均价仍 2.00,金额 12(出库行单价 5.00 为售价,不得参与成本)。
     */
    @Test
    void outboundConsumesAtAvgIgnoringSalePrice() {
        String inDoc = insertInboundDoc(wh1, itemA, "COST-IN-2", "10", "2.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "10", "inbound", inDoc, at(9));
        String outDoc = insertOutboundDoc(wh1, "COST-OUT-2", "4", "5.00");
        insertTx(wh1, itemA, 0L, 0L, "-4", "6", "outbound", outDoc, at(10));
        CostReportRow row = rowOf(allRows(), wh1, itemA, 0L);
        assertEquals("6", row.quantity());
        assertEquals("2.0000", row.avgPrice(), "出库后均价不变");
        assertEquals("12.00", row.amount(), "金额 = 10 - 4×2.00,售价 5.00 无关");
    }

    /**
     * 用例 3:再入库 10@3.00 → 均价 (12+30)/16 = 2.625,金额 42。
     */
    @Test
    void secondInboundRebalancesAvg() {
        String in1 = insertInboundDoc(wh1, itemA, "COST-IN-3A", "10", "2.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "10", "inbound", in1, at(8));
        String out = insertOutboundDoc(wh1, "COST-OUT-3", "4", "5.00");
        insertTx(wh1, itemA, 0L, 0L, "-4", "6", "outbound", out, at(9));
        String in2 = insertInboundDoc(wh1, itemA, "COST-IN-3B", "10", "3.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "16", "inbound", in2, at(10));
        CostReportRow row = rowOf(allRows(), wh1, itemA, 0L);
        assertEquals("16", row.quantity());
        assertEquals("2.6250", row.avgPrice());
        assertEquals("42.00", row.amount());
    }

    /**
     * 用例 4:调拨 6 件到仓 2 → 源仓金额减 6×2.625,目的仓金额加同样数(均价随货)。
     */
    @Test
    void transferCarriesSourceAvg() {
        String in1 = insertInboundDoc(wh1, itemA, "COST-IN-4A", "10", "2.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "10", "inbound", in1, at(8));
        String out = insertOutboundDoc(wh1, "COST-OUT-4", "4", "5.00");
        insertTx(wh1, itemA, 0L, 0L, "-4", "6", "outbound", out, at(9));
        String in2 = insertInboundDoc(wh1, itemA, "COST-IN-4B", "10", "3.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "16", "inbound", in2, at(9, 30));
        // 调拨 6:同单号先出后入(同时间戳按 id 排序)
        String tf = "COST-DB-4";
        insertTransferDoc(tf, wh1, wh2);
        insertTx(wh1, itemA, 0L, 0L, "-6", "10", "transfer_out", tf, at(10));
        insertTx(wh2, itemA, 0L, 0L, "6", "6", "transfer_in", tf, at(10, 0, 1));
        List<CostReportRow> rows = allRows();
        CostReportRow src = rowOf(rows, wh1, itemA, 0L);
        assertEquals("10", src.quantity());
        assertEquals("26.25", src.amount(), "源仓金额 = 42 - 6×2.625");
        assertEquals("2.6250", src.avgPrice());
        CostReportRow dst = rowOf(rows, wh2, itemA, 0L);
        assertEquals("6", dst.quantity());
        assertEquals("15.75", dst.amount(), "目的仓金额 = 6×源仓当时均价");
        assertEquals("2.6250", dst.avgPrice(), "均价随货走");
        // 合计行
        CostReportVO vo = costReportService.costReport(new CostReportQuery());
        assertEquals("16", vo.totalQuantity());
        assertEquals("42.00", vo.totalAmount());
    }

    /**
     * 用例 5:盘亏 2 → 金额减 2×2.625,均价不变。
     */
    @Test
    void adjustOutLossKeepsAvg() {
        String baseDoc = insertInboundDoc(wh1, itemA, "COST-IN-5", "16", "2.625", 0L);
        insertTx(wh1, itemA, 0L, 0L, "16", "16", "inbound", baseDoc, at(9));
        String tz = "COST-TZ-5";
        insertTx(wh1, itemA, 0L, 0L, "-2", "14", "adjust_out", tz, at(10));
        CostReportRow row = rowOf(allRows(), wh1, itemA, 0L);
        assertEquals("14", row.quantity());
        assertEquals("36.75", row.amount(), "金额 = 16×2.625 - 2×2.625");
        assertEquals("2.6250", row.avgPrice(), "盘亏均价不变");
    }

    /**
     * 用例 6:盘盈 2 → 金额加 2×2.625,均价不变。
     */
    @Test
    void adjustInGainKeepsAvg() {
        String baseDoc = insertInboundDoc(wh1, itemA, "COST-IN-6", "16", "2.625", 0L);
        insertTx(wh1, itemA, 0L, 0L, "16", "16", "inbound", baseDoc, at(9));
        String tz = "COST-TZ-6";
        insertTx(wh1, itemA, 0L, 0L, "2", "18", "adjust_in", tz, at(10));
        CostReportRow row = rowOf(allRows(), wh1, itemA, 0L);
        assertEquals("18", row.quantity());
        assertEquals("47.25", row.amount(), "金额 = 16×2.625 + 2×2.625");
        assertEquals("2.6250", row.avgPrice(), "盘盈按当前均价入账,均价不变");
    }

    /**
     * 用例 7:期初(第一条流水)5@1.50 权重正确;缺单据行单价的入库流水金额按 0 计入。
     */
    @Test
    void openingFirstWeightedAndMissingPriceZero() {
        String openingDoc = insertInboundDoc(wh1, itemB, "COST-IN-7A", "5", "1.50", 0L);
        insertTx(wh1, itemB, 0L, 0L, "5", "5", "inbound", openingDoc, at(8));
        // 物品 C:有流水无单据行 → 金额按 0
        insertTx(wh1, itemC, 0L, 0L, "8", "8", "inbound", "COST-IN-7B-无单据", at(9));
        CostReportRow openRow = rowOf(allRows(), wh1, itemB, 0L);
        assertEquals("5", openRow.quantity());
        assertEquals("1.5000", openRow.avgPrice());
        assertEquals("7.50", openRow.amount());
        CostReportRow zeroRow = rowOf(allRows(), wh1, itemC, 0L);
        assertEquals("8", zeroRow.quantity());
        assertEquals("0.0000", zeroRow.avgPrice(), "缺价按 0 计入");
        assertEquals("0.00", zeroRow.amount());
    }

    /**
     * 用例 8:数量对账——回放末数量 == 各库位 afterQty 之和(多库位 + 多批次)。
     */
    @Test
    void quantityMatchesAfterQty() {
        String in1 = insertInboundDoc(wh1, itemA, "COST-IN-8A", "10", "2.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "10", "inbound", in1, at(8));
        String out = insertOutboundDoc(wh1, "COST-OUT-8", "4", "5.00");
        insertTx(wh1, itemA, 0L, 0L, "-4", "6", "outbound", out, at(9));
        // 第二库位:入 3 出 1
        String in2 = insertInboundDoc(wh1, itemA, "COST-IN-8B", "3", "2.50", 0L);
        insertTx(wh1, itemA, 0L, 7L, "3", "3", "inbound", in2, at(9, 30));
        insertTx(wh1, itemA, 0L, 7L, "-1", "2", "outbound", out, at(10));
        // 批次 b1:入 5@4 出 2 → 3
        long b1 = insertBatch(itemA);
        String in3 = insertInboundDoc(wh1, itemA, "COST-IN-8C", "5", "4.00", b1);
        insertTx(wh1, itemA, b1, 0L, "5", "5", "inbound", in3, at(10, 30));
        insertTx(wh1, itemA, b1, 0L, "-2", "3", "outbound", out, at(11));
        List<CostReportRow> rows = allRows();
        CostReportRow noBatch = rowOf(rows, wh1, itemA, 0L);
        assertEquals("8", noBatch.quantity(), "库位 0 余额 6 + 库位 7 余额 2");
        CostReportRow batchRow = rowOf(rows, wh1, itemA, b1);
        assertEquals("3", batchRow.quantity());
        assertEquals("4.0000", batchRow.avgPrice());
        assertEquals("12.00", batchRow.amount());
        CostReportVO vo = costReportService.costReport(new CostReportQuery());
        assertEquals("11", vo.totalQuantity(), "合计 = 8 + 3");
    }

    /**
     * 用例 9:DataScope 口径——未授权查空;授权目的仓可见(金额依赖源仓回放);admin 全见。
     */
    @Test
    void dataScopeFiltering() {
        String in1 = insertInboundDoc(wh1, itemA, "COST-IN-9", "10", "2.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "10", "inbound", in1, at(8));
        String tf = "COST-DB-9";
        insertTransferDoc(tf, wh1, wh2);
        insertTx(wh1, itemA, 0L, 0L, "-4", "6", "transfer_out", tf, at(9));
        insertTx(wh2, itemA, 0L, 0L, "4", "4", "transfer_in", tf, at(9, 0, 1));

        // 未授权任何仓库 → 查空
        try {
            DataScope.set(List.of());
            CostReportVO none = costReportService.costReport(new CostReportQuery());
            assertTrue(none.rows().isEmpty());
            assertEquals("0.00", none.totalAmount());
        } finally {
            DataScope.clear();
        }
        // 仅授权目的仓:可见目的仓行,且金额来自源仓均价(回放不过滤仓)
        try {
            DataScope.set(List.of(wh2));
            CostReportVO scoped = costReportService.costReport(new CostReportQuery());
            assertEquals(1, scoped.rows().size());
            CostReportRow row = scoped.rows().get(0);
            assertEquals(wh2, row.warehouseId());
            assertEquals("4", row.quantity());
            assertEquals("8.00", row.amount());
        } finally {
            DataScope.clear();
        }
        // admin(未设置 DataScope)全见
        assertEquals(2, costReportService.costReport(new CostReportQuery()).rows().size());
    }

    /**
     * 用例 10:date 截止——回放截止后新增流水不计入。
     */
    @Test
    void dateCutoffExcludesLaterTx() {
        LocalDate day = LocalDate.now().minusDays(2);
        String in1 = insertInboundDoc(wh1, itemA, "COST-IN-10A", "10", "2.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "10", "10", "inbound", in1, day.atTime(10, 0, 0));
        String in2 = insertInboundDoc(wh1, itemA, "COST-IN-10B", "5", "3.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "5", "15", "inbound", in2,
                day.plusDays(2).atTime(10, 0, 0));

        // 截止 day:只算第一笔
        CostReportQuery toDay = new CostReportQuery();
        toDay.setDate(day.toString());
        CostReportRow before = rowOf(costReportService.costReport(toDay).rows(), wh1, itemA, 0L);
        assertEquals("10", before.quantity());
        assertEquals("20.00", before.amount());
        assertEquals("2.0000", before.avgPrice());
        // 截止 day+2:两笔都算
        CostReportQuery toLater = new CostReportQuery();
        toLater.setDate(day.plusDays(2).toString());
        CostReportRow after = rowOf(costReportService.costReport(toLater).rows(), wh1, itemA, 0L);
        assertEquals("15", after.quantity());
        assertEquals("35.00", after.amount());
        assertEquals("2.3333", after.avgPrice());
    }

    /**
     * 用例 11:调拨把源仓扣至 0——源仓保留最近均价,目的仓按源仓当时均价结转(防均价丢失)。
     */
    @Test
    void transferExhaustingSourceKeepsAvg() {
        String inDoc = insertInboundDoc(wh1, itemA, "COST-IN-11", "5", "1.00", 0L);
        insertTx(wh1, itemA, 0L, 0L, "5", "5", "inbound", inDoc, at(9));
        String tf = "COST-DB-11";
        insertTransferDoc(tf, wh1, wh2);
        insertTx(wh1, itemA, 0L, 0L, "-5", "0", "transfer_out", tf, at(10));
        insertTx(wh2, itemA, 0L, 0L, "5", "5", "transfer_in", tf, at(10, 0, 1));
        List<CostReportRow> rows = allRows();
        CostReportRow src = rowOf(rows, wh1, itemA, 0L);
        assertEquals("0", src.quantity());
        assertEquals("1.0000", src.avgPrice(), "源仓扣至 0 后保留最近均价");
        assertEquals("0.00", src.amount());
        CostReportRow dst = rowOf(rows, wh2, itemA, 0L);
        assertEquals("5", dst.quantity());
        assertEquals("5.00", dst.amount(), "目的仓按源仓当时均价结转");
        assertEquals("1.0000", dst.avgPrice());
    }

    // ==================== 造数/取数辅助 ====================

    /**
     * 无筛选查询全量报表行。
     *
     * @return 报表行
     */
    private List<CostReportRow> allRows() {
        return costReportService.costReport(new CostReportQuery()).rows();
    }

    /**
     * 从报表行中按 仓+物品+批次 找行。
     *
     * @param rows    报表行
     * @param whId    仓库 ID
     * @param itemId  物品 ID
     * @param batchId 批次 ID(0 无批次)
     * @return 匹配行
     */
    private CostReportRow rowOf(List<CostReportRow> rows, long whId, long itemId, long batchId) {
        return rows.stream()
                .filter(r -> r.warehouseId() == whId && r.itemId() == itemId
                        && r.batchId() == batchId)
                .findFirst().orElseThrow(() ->
                        new AssertionError("缺少单元行: wh=" + whId + " item=" + itemId
                                + " batch=" + batchId + " rows=" + rows));
    }

    /**
     * 今天某时点。
     *
     * @param hour 小时
     * @return 时间
     */
    private LocalDateTime at(int hour) {
        return at(hour, 0, 0);
    }

    /**
     * 今天某时点(分/秒)。
     *
     * @param hour  小时
     * @param minute 分钟
     * @return 时间
     */
    private LocalDateTime at(int hour, int minute) {
        return at(hour, minute, 0);
    }

    /**
     * 今天某时点(秒)。
     *
     * @param hour  小时
     * @param minute 分钟
     * @param second 秒
     * @return 时间
     */
    private LocalDateTime at(int hour, int minute, int second) {
        return LocalDate.now().atTime(LocalTime.of(hour, minute, second));
    }

    /**
     * 造仓库。
     *
     * @param code 编码
     * @return 仓库 ID
     */
    private long insertWarehouse(String code) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + "仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(false);
        wh.setEnableExpiry(false);
        wh.setEnableSerial(false);
        wh.setEnableLocation(false);
        warehouseMapper.insert(wh);
        return wh.getId();
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
        item.setStatus(1);
        itemMapper.insert(item);
        return item.getId();
    }

    /**
     * 造批次。
     *
     * @param itemId 物品 ID
     * @return 批次 ID
     */
    private long insertBatch(long itemId) {
        jdbcTemplate.update(
                "INSERT INTO batch (item_id, batch_no, production_date, status) "
                        + "VALUES (?, ?, now()::date, 'active')",
                itemId, "COST-B" + itemId);
        return jdbcTemplate.queryForObject("SELECT id FROM batch WHERE batch_no = ?",
                Long.class, "COST-B" + itemId);
    }

    /**
     * 造入库单头 + 行(行单价参与成本回放)。
     *
     * @param whId     仓库 ID
     * @param itemId   物品 ID
     * @param docNo    单号
     * @param qty      数量
     * @param unitPrice 行单价
     * @param batchId  批次 ID(0 无批次)
     * @return 单据号(流水 docNo 关联用)
     */
    private String insertInboundDoc(long whId, long itemId, String docNo, String qty,
            String unitPrice, long batchId) {
        jdbcTemplate.update(
                "INSERT INTO inbound_doc (doc_no, warehouse_id, status, doc_date, creator) "
                        + "VALUES (?, ?, 'finished', now()::date, 'cost_test')",
                docNo, whId);
        Long docId = jdbcTemplate.queryForObject("SELECT id FROM inbound_doc WHERE doc_no = ?",
                Long.class, docNo);
        jdbcTemplate.update(
                "INSERT INTO inbound_doc_item (doc_id, item_id, quantity, batch_id, location_id, unit_price) "
                        + "VALUES (?, ?, ?, ?, 0, ?)",
                docId, itemId, new BigDecimal(qty), batchId, new BigDecimal(unitPrice));
        return docNo;
    }

    /**
     * 造出库单头 + 行(行单价为售价,成本回放不使用)。
     *
     * @param whId      仓库 ID
     * @param docNo     单号
     * @param qty       数量
     * @param salePrice 售价
     * @return 单据号(流水 docNo 关联用)
     */
    private String insertOutboundDoc(long whId, String docNo, String qty, String salePrice) {
        jdbcTemplate.update(
                "INSERT INTO outbound_doc (doc_no, warehouse_id, status, doc_date, creator) "
                        + "VALUES (?, ?, 'finished', now()::date, 'cost_test')",
                docNo, whId);
        Long docId = jdbcTemplate.queryForObject("SELECT id FROM outbound_doc WHERE doc_no = ?",
                Long.class, docNo);
        jdbcTemplate.update(
                "INSERT INTO outbound_doc_item (doc_id, item_id, quantity, batch_id, location_id, unit_price) "
                        + "VALUES (?, ?, ?, 0, 0, ?)",
                docId, itemA, new BigDecimal(qty), new BigDecimal(salePrice));
        return docNo;
    }

    /**
     * 造调拨单头(调拨入取源仓用)。
     *
     * @param docNo  单号
     * @param fromWh 源仓 ID
     * @param toWh   目的仓 ID
     */
    private void insertTransferDoc(String docNo, long fromWh, long toWh) {
        jdbcTemplate.update(
                "INSERT INTO transfer_doc (doc_no, doc_date, from_warehouse_id, to_warehouse_id, status, creator) "
                        + "VALUES (?, now()::date, ?, ?, 'completed', 'cost_test')",
                docNo, fromWh, toWh);
    }

    /**
     * 造流水(时间可控)。
     *
     * @param whId      仓库 ID
     * @param itemId    物品 ID
     * @param batchId   批次 ID(0 无批次)
     * @param locId     库位 ID(0 无库位)
     * @param changeQty 变动量
     * @param afterQty  变动后数量
     * @param bizCode   业务编码
     * @param docNo     单据号
     * @param ts        流水时间
     */
    private void insertTx(long whId, long itemId, long batchId, long locId, String changeQty,
            String afterQty, String bizCode, String docNo, LocalDateTime ts) {
        jdbcTemplate.update(
                "INSERT INTO stock_transaction (warehouse_id, item_id, batch_id, location_id, "
                        + "change_qty, after_qty, biz_code, doc_no, operator, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'cost_test', ?)",
                whId, itemId, batchId, locId, new BigDecimal(changeQty),
                new BigDecimal(afterQty), bizCode, docNo, ts);
    }
}
