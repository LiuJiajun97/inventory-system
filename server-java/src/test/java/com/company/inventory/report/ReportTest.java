package com.company.inventory.report;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.report.PurchaseReconQuery;
import com.company.inventory.model.query.report.StockAgeingQuery;
import com.company.inventory.model.query.report.StockMonthlyQuery;
import com.company.inventory.model.vo.report.PurchaseReconVO;
import com.company.inventory.model.vo.report.StockAgeingVO;
import com.company.inventory.model.vo.report.StockMonthlyVO;
import com.company.inventory.service.ReportService;

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
 * 报表中心测试:进销存月报(期初/入/出/期末+金额快照+交叉核对+无流水物品)+
 * 库龄区间归属与呆滞标记(含无批次仓)+ 采购对账汇总/净采购与数据权限。
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
class ReportTest {

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
    /** JDBC */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** 报表服务 */
    @Autowired
    private ReportService reportService;

    /** 普通仓(无批次) */
    private long whId;
    /** 批次仓 */
    private long batchWhId;
    /** 物品 A */
    private long itemIdA;
    /** 物品 B */
    private long itemIdB;
    /** 供应商 S1 */
    private long supplierId;
    /** 客户 C1 */
    private long customerId;

    /**
     * 前置:清库并造基础主数据(两仓两物一供应商一客户)。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"opening_stock_doc_item\",\"opening_stock_doc\","
                + "\"inbound_doc_item\",\"inbound_doc\",\"outbound_doc_item\",\"outbound_doc\","
                + "\"purchase_return_item\",\"sales_return_item\",\"purchase_return\",\"sales_return\","
                + "\"purchase_order_item\",\"sales_order_item\",\"transfer_doc_item\","
                + "\"stocktake_doc_item\",\"stock_adjust_doc_item\",\"purchase_order\",\"sales_order\","
                + "\"transfer_doc\",\"stocktake_doc\",\"stock_adjust_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\","
                + "\"location\",\"item\",\"warehouse\",\"supplier\",\"customer\" "
                + "RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);
        whId = insertWarehouse("RP-WH", false);
        batchWhId = insertWarehouse("RP-BWH", true);
        itemIdA = insertItem("RP-A", "月报物品A", "件");
        itemIdB = insertItem("RP-B", "月报物品B", "件");
        supplierId = insertSupplier("RP-S1", "供应商一");
        insertCustomer("RP-C1", "客户一");
    }

    /**
     * 每用例前置:清空单据/库存/流水/批次(用例间隔离,主数据保留)。
     */
    @BeforeEach
    void resetStock() {
        jdbcTemplate.execute("TRUNCATE \"inbound_doc_item\",\"inbound_doc\","
                + "\"outbound_doc_item\",\"outbound_doc\","
                + "\"purchase_return_item\",\"purchase_return\",\"purchase_order_item\",\"purchase_order\","
                + "\"sales_return_item\",\"sales_return\",\"sales_order_item\",\"sales_order\","
                + "\"stock_transaction\",\"stock\",\"batch\"");
    }

    /**
     * 用例 1:月报正常——期初 10(区间前入库)+ 期间入库 5 + 期间出库 3
     * → 期初/入/出/期末 = 10/5/3/12,金额快照一致,期初+入-出=期末(交叉核对)。
     */
    @Test
    void monthlyReportNormal() {
        LocalDate today = LocalDate.now();
        // 期初:30 天前入库 10(单据日期与流水时间同天)
        long inDocA = insertInboundDoc(whId, "RP-IN-A", today.minusDays(30));
        insertInboundItem(inDocA, itemIdA, "10", "10.00");
        insertTx(whId, itemIdA, 0L, 0L, "10", "10", "inbound", "RP-IN-A",
                at(today.minusDays(30), 10));
        insertStock(whId, itemIdA, 0L, 0L, "10");
        // 期间入库 5(8 天前,行价税合计 10.00)
        long inDocB = insertInboundDoc(whId, "RP-IN-B", today.minusDays(8));
        insertInboundItem(inDocB, itemIdA, "5", "10.00");
        insertTx(whId, itemIdA, 0L, 0L, "5", "15", "inbound", "RP-IN-B",
                at(today.minusDays(8), 10));
        insertStock(whId, itemIdA, 0L, 0L, "15");
        // 期间出库 3(2 天前,行价税合计 9.00)
        long outDoc = insertOutboundDoc(whId, "RP-OUT-A", today.minusDays(2));
        insertOutboundItem(outDoc, itemIdA, "3", "9.00");
        insertTx(whId, itemIdA, 0L, 0L, "-3", "12", "outbound", "RP-OUT-A",
                at(today.minusDays(2), 10));
        insertStock(whId, itemIdA, 0L, 0L, "12");

        StockMonthlyQuery query = new StockMonthlyQuery();
        query.setFrom(today.minusDays(10).toString());
        query.setTo(today.toString());
        PageResult<StockMonthlyVO> page = reportService.monthlyReport(query);
        assertEquals(1L, page.total());
        StockMonthlyVO row = page.rows().get(0);
        assertEquals(itemIdA, row.itemId());
        assertEquals("10", row.openingQty(), "期初量应为 10");
        assertEquals("5", row.inQty(), "本期入量应为 5");
        assertEquals("3", row.outQty(), "本期出量应为 3");
        assertEquals("12", row.closingQty(), "期末量应为 12");
        assertEquals("10.00", row.inAmount(), "入库金额应取期间单据行价税合计");
        assertEquals("9.00", row.outAmount(), "出库金额应取期间单据行价税合计");
        // 交叉核对:期初 + 入 - 出 = 期末
        BigDecimal check = new BigDecimal(row.openingQty())
                .add(new BigDecimal(row.inQty()))
                .subtract(new BigDecimal(row.outQty()));
        assertEquals(0, check.compareTo(new BigDecimal(row.closingQty())),
                "期初+入-出 应等于 期末: " + row);
        // 行展开:各仓期末明细含本仓 12
        assertEquals(1, row.details().size());
        assertEquals(whId, row.details().get(0).warehouseId());
        assertEquals("12", row.details().get(0).closingQty());
    }

    /**
     * 用例 2:月报期间内无流水物品——期初=期末=当前量,入/出为 0,金额为空(无快照)。
     */
    @Test
    void monthlyReportNoTxInPeriod() {
        LocalDate today = LocalDate.now();
        // 60 天前入库 7(区间外),期间(近 10 天)无任何流水
        long inDoc = insertInboundDoc(whId, "RP-IN-C", today.minusDays(60));
        insertInboundItem(inDoc, itemIdB, "7", null);
        insertTx(whId, itemIdB, 0L, 0L, "7", "7", "inbound", "RP-IN-C",
                at(today.minusDays(60), 9));
        insertStock(whId, itemIdB, 0L, 0L, "7");

        StockMonthlyQuery query = new StockMonthlyQuery();
        query.setFrom(today.minusDays(10).toString());
        query.setTo(today.toString());
        PageResult<StockMonthlyVO> page = reportService.monthlyReport(query);
        assertEquals(1L, page.total());
        StockMonthlyVO row = page.rows().get(0);
        assertEquals(itemIdB, row.itemId());
        assertEquals("7", row.openingQty(), "期初量应为区间前最后流水 after_qty=7");
        assertEquals("0", row.inQty());
        assertEquals("0", row.outQty());
        assertEquals("7", row.closingQty(), "期末量应等于当前量 7");
        assertNull(row.inAmount(), "无期间入库单时入库金额应为空");
        assertNull(row.outAmount(), "无期间出库单时出库金额应为空");
    }

    /**
     * 用例 3:库龄区间归属(0-30/31-90/91-180/>180)+ 呆滞标记
     * (物品+仓 90 天内无出方向流水且当前量 > 0)+ 无批次仓按 item+仓库 汇总
     * (生产时间取该仓最早入方向流水日期)。
     */
    @Test
    void ageingBucketsAndStagnant() {
        LocalDate today = LocalDate.now();
        // 批次仓物品 A:4 个批次覆盖 4 个库龄区间,均无出库 → 全部呆滞
        long b1 = insertBatch(itemIdA, "RP-B1", today.minusDays(10));
        long b2 = insertBatch(itemIdA, "RP-B2", today.minusDays(50));
        long b3 = insertBatch(itemIdA, "RP-B3", today.minusDays(120));
        long b4 = insertBatch(itemIdA, "RP-B4", today.minusDays(300));
        insertTx(batchWhId, itemIdA, b1, 0L, "1", "1", "inbound", "RP-IN-D1", at(today.minusDays(10), 8));
        insertTx(batchWhId, itemIdA, b2, 0L, "1", "1", "inbound", "RP-IN-D2", at(today.minusDays(50), 8));
        insertTx(batchWhId, itemIdA, b3, 0L, "1", "1", "inbound", "RP-IN-D3", at(today.minusDays(120), 8));
        insertTx(batchWhId, itemIdA, b4, 0L, "1", "1", "inbound", "RP-IN-D4", at(today.minusDays(300), 8));
        insertStock(batchWhId, itemIdA, b1, 0L, "1");
        insertStock(batchWhId, itemIdA, b2, 0L, "1");
        insertStock(batchWhId, itemIdA, b3, 0L, "1");
        insertStock(batchWhId, itemIdA, b4, 0L, "1");
        // 批次仓物品 B:5 天前入库且 5 天前出库 → 非呆滞
        long b5 = insertBatch(itemIdB, "RP-B5", today.minusDays(5));
        insertTx(batchWhId, itemIdB, b5, 0L, "2", "2", "inbound", "RP-IN-E1", at(today.minusDays(5), 8));
        insertTx(batchWhId, itemIdB, b5, 0L, "-1", "1", "outbound", "RP-OUT-E1", at(today.minusDays(5), 9));
        insertStock(batchWhId, itemIdB, b5, 0L, "1");
        // 无批次仓物品 B:20 天前入库(生产时间取最早入方向流水),无出库 → 呆滞,批次号为空
        insertTx(whId, itemIdB, 0L, 0L, "3", "3", "inbound", "RP-IN-F1", at(today.minusDays(20), 8));
        insertStock(whId, itemIdB, 0L, 0L, "3");

        StockAgeingQuery query = new StockAgeingQuery();
        PageResult<StockAgeingVO> page = reportService.ageingReport(query);
        assertEquals(6L, page.total(), "应有 6 行:批次仓 5 个批次 + 无批次仓 1 行汇总");

        StockAgeingVO rowB1 = findAgeing(page.rows(), b1);
        assertEquals("0-30", rowB1.ageBucket(), "库龄 10 天应归 0-30: " + rowB1);
        assertEquals("RP-B1", rowB1.batchNo());
        assertTrue(rowB1.stagnant(), "物品A+批次仓 90 天无出库应为呆滞");
        assertEquals("31-90", findAgeing(page.rows(), b2).ageBucket());
        assertEquals("91-180", findAgeing(page.rows(), b3).ageBucket());
        assertEquals(">180", findAgeing(page.rows(), b4).ageBucket());
        assertTrue(findAgeing(page.rows(), b4).stagnant());

        // 物品 B 批次行:近期有出库 → 非呆滞
        StockAgeingVO rowB5 = findAgeing(page.rows(), b5);
        assertEquals("0-30", rowB5.ageBucket());
        assertTrue(Boolean.FALSE.equals(rowB5.stagnant()), "5 天前有出库不应呆滞");

        // 无批次仓:批次号为空,生产时间=该仓最早入方向流水日期(20 天前),呆滞
        StockAgeingVO rowNoBatch = page.rows().stream()
                .filter(r -> r.warehouseId() == whId && r.itemId() == itemIdB)
                .findFirst().orElseThrow();
        assertNull(rowNoBatch.batchNo(), "无批次仓行批次号应为空");
        assertEquals(today.minusDays(20), rowNoBatch.productionDate(),
                "无批次仓生产时间应取最早入方向流水日期");
        assertEquals("0-30", rowNoBatch.ageBucket());
        assertTrue(rowNoBatch.stagnant(), "无出库应呆滞");
        assertEquals("3", rowNoBatch.quantity());
    }

    /**
     * 用例 4:采购对账——2 采购单(15/150.00)+ 1 退货(2/20.00)
     * → 汇总与净采购正确;未授权仓查空;授权退货所在仓可见(采购单经关联入库单仓库)。
     */
    @Test
    void purchaseReconAndDataScope() {
        LocalDate today = LocalDate.now();
        // 采购单 1:8 天前,10 件 100.00;关联入库单(入库仓 whId,数据权限口径)
        long po1 = insertPurchaseOrder(supplierId, "RP-PO1", today.minusDays(8));
        insertPurchaseOrderItem(po1, itemIdA, "10", "100.00");
        insertInboundDocRef(whId, "RP-IN-P1", po1);
        // 采购单 2:2 天前,5 件 50.00
        long po2 = insertPurchaseOrder(supplierId, "RP-PO2", today.minusDays(2));
        insertPurchaseOrderItem(po2, itemIdA, "5", "50.00");
        insertInboundDocRef(whId, "RP-IN-P2", po2);
        // 退货单:1 天前(退货仓 whId),2 件 20.00
        long pr1 = insertPurchaseReturn(po1, whId, "RP-PR1", today.minusDays(1));
        insertPurchaseReturnItem(pr1, itemIdA, "2", "20.00");

        PurchaseReconQuery query = new PurchaseReconQuery();
        query.setFrom(today.minusDays(10).toString());
        query.setTo(today.toString());

        // 未授权任何仓库 → 查空
        try {
            DataScope.set(List.of(999999L));
            PageResult<PurchaseReconVO> none = reportService.purchaseRecon(query);
            assertEquals(0L, none.total());
            assertTrue(none.rows().isEmpty());
            assertEquals(1L, none.page());
            assertEquals(20L, none.pageSize());
        } finally {
            DataScope.clear();
        }
        // 授权退货/入库所在仓 → 可见且汇总/净采购正确
        try {
            DataScope.set(List.of(whId));
            PageResult<PurchaseReconVO> scoped = reportService.purchaseRecon(query);
            assertEquals(1L, scoped.total());
            PurchaseReconVO row = scoped.rows().get(0);
            assertEquals(supplierId, row.supplierId());
            assertEquals(2L, row.orderCount());
            assertEquals("15", row.orderQty());
            assertEquals("150.00", row.orderAmount());
            assertEquals("2", row.returnQty());
            assertEquals("20.00", row.returnAmount());
            assertEquals("13", row.netQty(), "净采购量 = 15 - 2");
            assertEquals("130.00", row.netAmount(), "净采购金额 = 150 - 20");
            // 行展开:2 采购单 + 1 退货单明细
            assertEquals(3, row.details().size());
        } finally {
            DataScope.clear();
        }
        // admin 豁免(未设置 DataScope):全可见
        PageResult<PurchaseReconVO> all = reportService.purchaseRecon(query);
        assertEquals(1L, all.total());
    }

    // ==================== 造数辅助 ====================

    /**
     * 从库龄结果中按批次 ID 找行。
     *
     * @param rows   当前页行
     * @param batchId 批次 ID
     * @return 匹配行
     */
    private StockAgeingVO findAgeing(List<StockAgeingVO> rows, long batchId) {
        return rows.stream().filter(r -> r.batchId().equals(batchId)).findFirst().orElseThrow();
    }

    /**
     * 某天某时点(东八区)。
     *
     * @param day  日期
     * @param hour 小时
     * @return 时间
     */
    private LocalDateTime at(LocalDate day, int hour) {
        return day.atTime(LocalTime.of(hour, 0, 0));
    }

    /**
     * 造仓库。
     *
     * @param code  编码
     * @param batch 是否批次仓
     * @return 仓库 ID
     */
    private long insertWarehouse(String code, boolean batch) {
        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code + "仓");
        wh.setWarehouseType("raw");
        wh.setEnableBatch(batch);
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
     * @param name 名称
     * @param unit 单位
     * @return 物品 ID
     */
    private long insertItem(String code, String name, String unit) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(name);
        item.setUnit(unit);
        item.setStatus(1);
        itemMapper.insert(item);
        return item.getId();
    }

    /**
     * 造供应商。
     *
     * @param code 编码
     * @param name 名称
     * @return 供应商 ID
     */
    private long insertSupplier(String code, String name) {
        SupplierDO sup = new SupplierDO();
        sup.setSupplierCode(code);
        sup.setSupplierName(name);
        sup.setStatus(1);
        supplierMapper.insert(sup);
        return sup.getId();
    }

    /**
     * 造客户。
     *
     * @param code 编码
     * @param name 名称
     * @return 客户 ID
     */
    private long insertCustomer(String code, String name) {
        CustomerDO cus = new CustomerDO();
        cus.setCustomerCode(code);
        cus.setCustomerName(name);
        cus.setStatus(1);
        customerMapper.insert(cus);
        return cus.getId();
    }

    /**
     * 造批次。
     *
     * @param itemId  物品 ID
     * @param batchNo 批次号
     * @param prodDate 生产日期
     * @return 批次 ID
     */
    private long insertBatch(long itemId, String batchNo, LocalDate prodDate) {
        jdbcTemplate.update(
                "INSERT INTO batch (item_id, batch_no, production_date, status) VALUES (?, ?, ?, 'active')",
                itemId, batchNo, prodDate);
        return jdbcTemplate.queryForObject("SELECT id FROM batch WHERE batch_no = ?",
                Long.class, batchNo);
    }

    /**
     * 造库存行。
     *
     * @param whId     仓库 ID
     * @param itemId   物品 ID
     * @param batchId  批次 ID(0 无批次)
     * @param locId    库位 ID(0 无库位)
     * @param qty      数量
     */
    private void insertStock(long whId, long itemId, long batchId, long locId, String qty) {
        jdbcTemplate.update(
                "INSERT INTO stock (warehouse_id, item_id, batch_id, location_id, quantity, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, now()) "
                        + "ON CONFLICT (warehouse_id, item_id, batch_id, location_id) "
                        + "DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()",
                whId, itemId, batchId, locId, new BigDecimal(qty));
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
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'rp_test', ?)",
                whId, itemId, batchId, locId, new BigDecimal(changeQty),
                new BigDecimal(afterQty), bizCode, docNo, ts);
    }

    /**
     * 造入库单头(finished,doc_date 可控)。
     *
     * @param whId    仓库 ID
     * @param docNo   单号
     * @param docDate 单据日期
     * @return 单据 ID
     */
    private long insertInboundDoc(long whId, String docNo, LocalDate docDate) {
        jdbcTemplate.update(
                "INSERT INTO inbound_doc (doc_no, warehouse_id, status, doc_date, creator) "
                        + "VALUES (?, ?, 'finished', ?, 'rp_test')",
                docNo, whId, docDate);
        return jdbcTemplate.queryForObject("SELECT id FROM inbound_doc WHERE doc_no = ?",
                Long.class, docNo);
    }

    /**
     * 造入库单行(价税合计快照可空)。
     *
     * @param docId  单据 ID
     * @param itemId 物品 ID
     * @param qty    数量
     * @param taxInclusiveTotal 价税合计(可空)
     */
    private void insertInboundItem(long docId, long itemId, String qty, String taxInclusiveTotal) {
        jdbcTemplate.update(
                "INSERT INTO inbound_doc_item (doc_id, item_id, quantity, batch_id, location_id, "
                        + "tax_inclusive_total) VALUES (?, ?, ?, 0, 0, ?)",
                docId, itemId, new BigDecimal(qty), taxInclusiveTotal == null
                        ? null : new BigDecimal(taxInclusiveTotal));
    }

    /**
     * 造采购关联入库单(数据权限口径:采购单经其入库单仓库过滤)。
     *
     * @param whId  仓库 ID
     * @param docNo 单号
     * @param poId  采购单 ID
     */
    private void insertInboundDocRef(long whId, String docNo, long poId) {
        jdbcTemplate.update(
                "INSERT INTO inbound_doc (doc_no, warehouse_id, status, ref_type, ref_doc_id, "
                        + "doc_date, creator) VALUES (?, ?, 'finished', 'purchase', ?, now(), 'rp_test')",
                docNo, whId, poId);
    }

    /**
     * 造出库单头(finished)。
     *
     * @param whId    仓库 ID
     * @param docNo   单号
     * @param docDate 单据日期
     * @return 单据 ID
     */
    private long insertOutboundDoc(long whId, String docNo, LocalDate docDate) {
        jdbcTemplate.update(
                "INSERT INTO outbound_doc (doc_no, warehouse_id, status, doc_date, creator) "
                        + "VALUES (?, ?, 'finished', ?, 'rp_test')",
                docNo, whId, docDate);
        return jdbcTemplate.queryForObject("SELECT id FROM outbound_doc WHERE doc_no = ?",
                Long.class, docNo);
    }

    /**
     * 造出库单行。
     *
     * @param docId  单据 ID
     * @param itemId 物品 ID
     * @param qty    数量
     * @param taxInclusiveTotal 价税合计
     */
    private void insertOutboundItem(long docId, long itemId, String qty, String taxInclusiveTotal) {
        jdbcTemplate.update(
                "INSERT INTO outbound_doc_item (doc_id, item_id, quantity, batch_id, location_id, "
                        + "tax_inclusive_total) VALUES (?, ?, ?, 0, 0, ?)",
                docId, itemId, new BigDecimal(qty), new BigDecimal(taxInclusiveTotal));
    }

    /**
     * 造采购订单头(finished)。
     *
     * @param supplierId 供应商 ID
     * @param docNo      单号
     * @param docDate    单据日期
     * @return 单据 ID
     */
    private long insertPurchaseOrder(long supplierId, String docNo, LocalDate docDate) {
        jdbcTemplate.update(
                "INSERT INTO purchase_order (doc_no, doc_date, supplier_id, buyer_id, status, creator) "
                        + "VALUES (?, ?, ?, 0, 'finished', 'rp_test')",
                docNo, docDate, supplierId);
        return jdbcTemplate.queryForObject("SELECT id FROM purchase_order WHERE doc_no = ?",
                Long.class, docNo);
    }

    /**
     * 造采购订单行。
     *
     * @param orderId  单据 ID
     * @param itemId   物品 ID
     * @param qty      订购数量
     * @param taxInclusiveTotal 价税合计
     */
    private void insertPurchaseOrderItem(long orderId, long itemId, String qty, String taxInclusiveTotal) {
        jdbcTemplate.update(
                "INSERT INTO purchase_order_item (order_id, line_no, item_id, ordered_qty, "
                        + "unit_price, tax_rate, amount, tax_amount, tax_inclusive_total, creator) "
                        + "VALUES (?, 1, ?, ?, 0, 0, 0, 0, ?, 'rp_test')",
                orderId, itemId, new BigDecimal(qty), new BigDecimal(taxInclusiveTotal));
    }

    /**
     * 造采购退货单头(finished)。
     *
     * @param poId    采购单 ID
     * @param whId    退货仓 ID
     * @param docNo   单号
     * @param docDate 单据日期
     * @return 单据 ID
     */
    private long insertPurchaseReturn(long poId, long whId, String docNo, LocalDate docDate) {
        jdbcTemplate.update(
                "INSERT INTO purchase_return (doc_no, doc_date, purchase_order_id, warehouse_id, "
                        + "status, creator) VALUES (?, ?, ?, ?, 'finished', 'rp_test')",
                docNo, docDate, poId, whId);
        return jdbcTemplate.queryForObject("SELECT id FROM purchase_return WHERE doc_no = ?",
                Long.class, docNo);
    }

    /**
     * 造采购退货行。
     *
     * @param docId  单据 ID
     * @param itemId 物品 ID
     * @param qty    退货数量
     * @param taxInclusiveTotal 价税合计
     */
    private void insertPurchaseReturnItem(long docId, long itemId, String qty, String taxInclusiveTotal) {
        jdbcTemplate.update(
                "INSERT INTO purchase_return_item (doc_id, line_no, purchase_order_item_id, item_id, "
                        + "quantity, unit_price, amount, tax_amount, tax_inclusive_total, creator) "
                        + "VALUES (?, 1, 1, ?, ?, 0, 0, 0, ?, 'rp_test')",
                docId, itemId, new BigDecimal(qty), new BigDecimal(taxInclusiveTotal));
    }
}
