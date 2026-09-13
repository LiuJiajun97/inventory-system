package com.company.inventory.doclines;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V17 单据明细行列表(/lines)测试:10 类单据明细拍平查询的代表性验证。
 *
 * <p>覆盖:采购订单/入库单/销售退货 /lines 200 + 行字段齐全 + 分页 total 正确;
 * itemKeyword/batchNo 过滤;数据权限(operator 仅授权 A 仓只可见 A 仓单据行,口径同主表);
 * viewer 只读 200。自造数据(JdbcTemplate 直落,前缀 DLT-)自清。</p>
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
class DocLinesTest {

    /** 本类单据号前缀(定向清理用)。 */
    private static final String DOC_PREFIX = "DLT-";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    /** 测试用户 ID。 */
    private long adminUserId;
    private long operatorUserId;
    private long viewerUserId;
    /** 授权仓 A。 */
    private long whAId;
    /** 仓 B。 */
    private long whBId;
    /** 物品 1/2。 */
    private long item1Id;
    private long item2Id;
    /** 入库单 A 仓(2 行)/B 仓(1 行)ID。 */
    private long inAId;
    /** 采购订单 ID。 */
    private long poId;

    /**
     * 前置:造用户/仓库/物品/对方单位 + 采购订单(2 行)/入库 A(2 行)/入库 B(1 行)/
     * 销售订单(1 行)+ 销售退货(1 行),operator 仅授权 A 仓。
     */
    @BeforeAll
    void setUp() {
        // 先清本类单据/用户授权仓/主数据残留(防上轮 tearDown 中断)
        cleanDocs();
        jdbc.update("DELETE FROM sys_user_warehouse WHERE user_id IN (SELECT id FROM sys_user "
                + "WHERE username IN ('dl_admin', 'dl_operator', 'dl_viewer'))");
        jdbc.update("DELETE FROM warehouse WHERE warehouse_code IN ('DLT-WH-A', 'DLT-WH-B')");
        jdbc.update("DELETE FROM item WHERE item_code IN ('DLT-IT-1', 'DLT-IT-2')");
        jdbc.update("DELETE FROM supplier WHERE supplier_code = 'DLT-SUP'");
        jdbc.update("DELETE FROM customer WHERE customer_code = 'DLT-CUS'");
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(4);
        for (String[] u : new String[][] {
                {"dl_admin", "admin"}, {"dl_operator", "operator"}, {"dl_viewer", "viewer"}}) {
            jdbc.update("DELETE FROM sys_user WHERE username = ?", u[0]);
            jdbc.update("INSERT INTO sys_user (username, password_hash, name, role, status) "
                    + "VALUES (?, ?, ?, ?, 1)", u[0], enc.encode(u[0] + "123"), u[0], u[1]);
        }
        adminUserId = userByName("dl_admin");
        operatorUserId = userByName("dl_operator");
        viewerUserId = userByName("dl_viewer");

        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type) "
                        + "VALUES ('DLT-WH-A', 'DLT 仓 A', 'normal')");
        whAId = jdbc.queryForObject("SELECT id FROM warehouse WHERE warehouse_code = 'DLT-WH-A'",
                Long.class);
        jdbc.update("INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type) "
                        + "VALUES ('DLT-WH-B', 'DLT 仓 B', 'normal')");
        whBId = jdbc.queryForObject("SELECT id FROM warehouse WHERE warehouse_code = 'DLT-WH-B'",
                Long.class);
        jdbc.update("INSERT INTO item (item_code, item_name, unit) VALUES ('DLT-IT-1', 'DLT 物品一', '个')");
        item1Id = jdbc.queryForObject("SELECT id FROM item WHERE item_code = 'DLT-IT-1'", Long.class);
        jdbc.update("INSERT INTO item (item_code, item_name, unit) VALUES ('DLT-IT-2', 'DLT 物品二', '个')");
        item2Id = jdbc.queryForObject("SELECT id FROM item WHERE item_code = 'DLT-IT-2'", Long.class);
        jdbc.update("INSERT INTO supplier (supplier_code, supplier_name) VALUES ('DLT-SUP', 'DLT 供应商')");
        jdbc.update("INSERT INTO customer (customer_code, customer_name) VALUES ('DLT-CUS', 'DLT 客户')");

        // 采购订单:2 行(物品一 2 件 / 物品二 3 件)
        jdbc.update("INSERT INTO purchase_order (doc_no, doc_date, supplier_id, buyer_id) "
                        + "VALUES ('DLT-PO-1', CURRENT_DATE, (SELECT id FROM supplier WHERE supplier_code = 'DLT-SUP'), ?)",
                adminUserId);
        poId = jdbc.queryForObject("SELECT id FROM purchase_order WHERE doc_no = 'DLT-PO-1'", Long.class);
        insertPoItem(poId, 1, item1Id, "2", "10.00", "20.00");
        insertPoItem(poId, 2, item2Id, "3", "20.00", "60.00");

        // 入库单 A 仓:2 行(物品一带批次 B-DLT-1 / 物品二无批次)
        jdbc.update("INSERT INTO inbound_doc (doc_no, warehouse_id, doc_date) VALUES ('DLT-IN-A', ?, CURRENT_DATE)",
                whAId);
        inAId = jdbc.queryForObject("SELECT id FROM inbound_doc WHERE doc_no = 'DLT-IN-A'", Long.class);
        jdbc.update("INSERT INTO inbound_doc_item (doc_id, item_id, quantity, batch_no, line_no, unit_price, amount) "
                        + "VALUES (?, ?, ?::numeric, 'B-DLT-1', 1, ?::numeric, ?::numeric)", inAId, item1Id, 5, 8.00, 40.00);
        jdbc.update("INSERT INTO inbound_doc_item (doc_id, item_id, quantity, line_no, unit_price, amount) "
                        + "VALUES (?, ?, ?::numeric, 2, ?::numeric, ?::numeric)", inAId, item2Id, 7, 9.00, 63.00);
        // 入库单 B 仓:1 行
        jdbc.update("INSERT INTO inbound_doc (doc_no, warehouse_id, doc_date) VALUES ('DLT-IN-B', ?, CURRENT_DATE)",
                whBId);
        long inBId = jdbc.queryForObject("SELECT id FROM inbound_doc WHERE doc_no = 'DLT-IN-B'", Long.class);
        jdbc.update("INSERT INTO inbound_doc_item (doc_id, item_id, quantity, line_no) VALUES (?, ?, ?::numeric, 1)",
                inBId, item2Id, 4);

        // 销售订单(1 行)+ 销售退货(1 行,仓 A)
        jdbc.update("INSERT INTO sales_order (doc_no, doc_date, customer_id, salesperson_id, warehouse_id) "
                        + "VALUES ('DLT-SO-1', CURRENT_DATE, (SELECT id FROM customer WHERE customer_code = 'DLT-CUS'), ?, ?)",
                adminUserId, whAId);
        long soId = jdbc.queryForObject("SELECT id FROM sales_order WHERE doc_no = 'DLT-SO-1'", Long.class);
        jdbc.update("INSERT INTO sales_order_item (order_id, line_no, item_id, ordered_qty, unit_price, "
                        + "amount, tax_amount, tax_inclusive_total) "
                        + "VALUES (?, 1, ?, ?::numeric, ?::numeric, ?::numeric, 0, ?::numeric)",
                soId, item1Id, 6, 30.00, 180.00, 180.00);
        long soItem1Id = jdbc.queryForObject(
                "SELECT id FROM sales_order_item WHERE order_id = ? AND line_no = 1", Long.class, soId);
        jdbc.update("INSERT INTO sales_return (doc_no, doc_date, sales_order_id, warehouse_id) "
                        + "VALUES ('DLT-SR-1', CURRENT_DATE, ?, ?)", soId, whAId);
        long srId = jdbc.queryForObject("SELECT id FROM sales_return WHERE doc_no = 'DLT-SR-1'", Long.class);
        jdbc.update("INSERT INTO sales_return_item (doc_id, sales_order_item_id, item_id, quantity, unit_price) "
                        + "VALUES (?, ?, ?, ?::numeric, ?::numeric)", srId, soItem1Id, item1Id, 2, 30.00);

        // operator 仅授权 A 仓
        jdbc.update("INSERT INTO sys_user_warehouse (user_id, warehouse_id) VALUES (?, ?)",
                operatorUserId, whAId);
    }

    /**
     * 收尾:定向删除本类数据(单据/主数据/用户授权/用户)。
     */
    @AfterAll
    void tearDown() {
        cleanDocs();
        jdbc.update("DELETE FROM sys_user_warehouse WHERE user_id IN (?, ?, ?)",
                adminUserId, operatorUserId, viewerUserId);        jdbc.update("DELETE FROM warehouse WHERE warehouse_code IN ('DLT-WH-A', 'DLT-WH-B')");
        jdbc.update("DELETE FROM item WHERE item_code IN ('DLT-IT-1', 'DLT-IT-2')");
        jdbc.update("DELETE FROM supplier WHERE supplier_code = 'DLT-SUP'");
        jdbc.update("DELETE FROM customer WHERE customer_code = 'DLT-CUS'");
        jdbc.update("DELETE FROM sys_user WHERE username IN ('dl_admin', 'dl_operator', 'dl_viewer')");
    }

    /**
     * 用例 1:admin 采购订单 /lines 200,行字段齐全,total=2(造 2 行)。
     */
    @Test
    void purchaseOrderLinesAdmin() {
        Map<String, Object> body = getAsMap("/api/v1/purchase-orders/lines", "dl_admin");
        assertEquals(2, totalOf(body), "采购订单明细行 total 应为 2");
        List<Map<String, Object>> rows = rowsOf(body);
        Map<String, Object> r = rows.get(0);
        assertEquals("DLT-PO-1", r.get("docNo"));
        assertNotNull(r.get("docId"));
        assertNotNull(r.get("docDate"));
        assertEquals("draft", r.get("status"));
        assertEquals("DLT 供应商", r.get("supplierName"));
        assertNotNull(r.get("lineNo"));
        assertEquals("DLT-IT-1", itemCodeOf(rows));
        assertNotNull(r.get("quantity"));
        assertNotNull(r.get("unitPrice"));
        assertNotNull(r.get("amount"));
        assertNotNull(r.get("taxInclusiveTotal"));
    }

    /**
     * 用例 2:admin 入库 /lines 分页:total=3(A 仓 2 行 + B 仓 1 行),pageSize=2 分两页。
     */
    @Test
    void inboundLinesPaging() {
        Map<String, Object> p1 = getAsMap("/api/v1/inbound/lines?pageSize=2&page=1", "dl_admin");
        assertEquals(3, totalOf(p1));
        assertEquals(2, rowsOf(p1).size());
        Map<String, Object> p2 = getAsMap("/api/v1/inbound/lines?pageSize=2&page=2", "dl_admin");
        assertEquals(1, rowsOf(p2).size());
        Map<String, Object> r = rowsOf(p1).get(0);
        assertTrue(List.of("DLT-IN-A", "DLT-IN-B").contains(String.valueOf(r.get("docNo"))));
        assertNotNull(r.get("warehouseName"));
        assertNotNull(r.get("quantity"));
    }

    /**
     * 用例 3:itemKeyword 过滤:入库明细按物品编码过滤只留物品一 1 行,
     * 采购订单明细按物品二过滤只留 1 行。
     */
    @Test
    void itemKeywordFilter() {
        Map<String, Object> byCode = getAsMap("/api/v1/inbound/lines?itemKeyword=DLT-IT-1", "dl_admin");
        assertEquals(1, totalOf(byCode));
        assertEquals("DLT 物品一", rowsOf(byCode).get(0).get("itemName"));
        Map<String, Object> byName = getAsMap("/api/v1/purchase-orders/lines?itemKeyword=物品二", "dl_admin");
        assertEquals(1, totalOf(byName));
        assertEquals("DLT-IT-2", rowsOf(byName).get(0).get("itemCode"));
    }

    /**
     * 用例 4:batchNo 精确过滤:入库明细 batchNo=B-DLT-1 只留 1 行。
     */
    @Test
    void batchNoFilter() {
        Map<String, Object> body = getAsMap("/api/v1/inbound/lines?batchNo=B-DLT-1", "dl_admin");
        assertEquals(1, totalOf(body));
        assertEquals("B-DLT-1", rowsOf(body).get(0).get("batchNo"));
    }

    /**
     * 用例 5:数据权限:operator 仅授权 A 仓,/inbound/lines 只见 A 仓 2 行,
     * 与主表 /inbound 口径一致(只见 A 仓 1 单)。
     */
    @Test
    void dataScopeOperatorOnlyWarehouseA() {
        Map<String, Object> lines = getAsMap("/api/v1/inbound/lines", "dl_operator");
        assertEquals(2, totalOf(lines));
        for (Map<String, Object> r : rowsOf(lines)) {
            assertEquals("DLT-IN-A", r.get("docNo"));
            assertEquals("DLT 仓 A", r.get("warehouseName"));
        }
        Map<String, Object> main = getAsMap("/api/v1/inbound", "dl_operator");
        assertEquals(1, totalOf(main), "主表口径:operator 只见 A 仓 1 单");
    }

    /**
     * 用例 6:viewer 只读:GET /lines 200。无数据权限的采购订单可见全部 2 行(字段齐全),
     * 仓维度单据未授权仓查空(与主表口径一致)。
     */
    @Test
    void viewerCanReadLines() {
        Map<String, Object> po = getAsMap("/api/v1/purchase-orders/lines", "dl_viewer");
        assertEquals(200, lastStatus());
        assertEquals(2, totalOf(po));
        assertEquals("DLT 供应商", rowsOf(po).get(0).get("supplierName"));
        Map<String, Object> sr = getAsMap("/api/v1/sales-returns/lines", "dl_viewer");
        assertEquals(200, lastStatus());
        assertEquals(0, totalOf(sr), "viewer 未授权任何仓,仓维度单据查空");
        // admin 视角:销售退货 /lines 字段齐全(客户名 JOIN 自销售订单)
        Map<String, Object> adminSr = getAsMap("/api/v1/sales-returns/lines", "dl_admin");
        assertEquals(1, totalOf(adminSr));
        assertEquals("DLT-SR-1", rowsOf(adminSr).get(0).get("docNo"));
        assertEquals("DLT 客户", rowsOf(adminSr).get(0).get("customerName"));
        assertNotNull(rowsOf(adminSr).get(0).get("quantity"));
    }

    /** 登录并取 token。 */
    private String login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + username + "123\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value());
        return String.valueOf(res.getBody().get("token"));
    }

    /** 最近一次响应状态码(测试内串行,单线程安全)。 */
    private int lastStatusCode;

    /** 最近一次 GET 的状态码。 */
    private int lastStatus() {
        return lastStatusCode;
    }

    /**
     * 带 token 的 GET /lines 类接口,返回 JSON 体。
     *
     * @param path  请求路径(含查询串)
     * @param user  登录用户名
     * @return 响应 JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAsMap(String path, String user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login(user));
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        lastStatusCode = res.getStatusCode().value();
        assertEquals(200, lastStatusCode, path + " 应 200");
        return res.getBody();
    }

    /** 分页结果 total。 */
    private long totalOf(Map<String, Object> body) {
        return ((Number) body.get("total")).longValue();
    }

    /** 分页结果 rows。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("rows");
    }

    /** 首行 itemCode(便捷断言)。 */
    private String itemCodeOf(List<Map<String, Object>> rows) {
        return String.valueOf(rows.get(0).get("itemCode"));
    }

    /** 按用户名查用户 ID。 */
    private long userByName(String username) {
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ?",
                Long.class, username);
    }

    /** 造采购订单行。 */
    private void insertPoItem(long orderId, int lineNo, long itemId, String qty, String price,
            String amount) {
        jdbc.update("INSERT INTO purchase_order_item (order_id, line_no, item_id, ordered_qty, "
                        + "unit_price, amount, tax_amount, tax_inclusive_total) "
                        + "VALUES (?, ?, ?, ?::numeric, ?::numeric, ?::numeric, 0, ?::numeric)",
                orderId, lineNo, itemId, qty, price, amount, amount);
    }

    /** 清理本类单据(先明细后主表)。 */
    private void cleanDocs() {
        List<Long> poIds = jdbc.queryForList(
                "SELECT id FROM purchase_order WHERE doc_no LIKE ?", Long.class, DOC_PREFIX + "%");
        if (!poIds.isEmpty()) {
            jdbc.update("DELETE FROM purchase_order_item WHERE order_id IN (" + idList(poIds) + ")");
        }
        List<Long> soIds = jdbc.queryForList(
                "SELECT id FROM sales_order WHERE doc_no LIKE ?", Long.class, DOC_PREFIX + "%");
        if (!soIds.isEmpty()) {
            jdbc.update("DELETE FROM sales_order_item WHERE order_id IN (" + idList(soIds) + ")");
        }
        List<Long> srIds = jdbc.queryForList(
                "SELECT id FROM sales_return WHERE doc_no LIKE ?", Long.class, DOC_PREFIX + "%");
        if (!srIds.isEmpty()) {
            jdbc.update("DELETE FROM sales_return_item WHERE doc_id IN (" + idList(srIds) + ")");
        }
        List<Long> inIds = jdbc.queryForList(
                "SELECT id FROM inbound_doc WHERE doc_no LIKE ?", Long.class, DOC_PREFIX + "%");
        if (!inIds.isEmpty()) {
            jdbc.update("DELETE FROM inbound_doc_item WHERE doc_id IN (" + idList(inIds) + ")");
        }
        jdbc.update("DELETE FROM purchase_order WHERE doc_no LIKE ?", DOC_PREFIX + "%");
        jdbc.update("DELETE FROM sales_return WHERE doc_no LIKE ?", DOC_PREFIX + "%");
        jdbc.update("DELETE FROM sales_order WHERE doc_no LIKE ?", DOC_PREFIX + "%");
        jdbc.update("DELETE FROM inbound_doc WHERE doc_no LIKE ?", DOC_PREFIX + "%");
    }

    /** ID 列表转 SQL IN 列表字面量(本类自造数据 ID,无注入风险)。 */
    private String idList(List<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
