package com.company.inventory.price;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.support.RbacSeedSupport;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SupplierMapper;

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
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 价目表测试(V26,真实 HTTP,RbacSeedSupport)。
 *
 * <p>覆盖:CRUD(创建/编辑/删除级联)、effectivePrices 命中(区间内/valid_until 可空/
 * 过期不命中/多条取 valid_from 最近/status=0 不命中)、行唯一 400、对方不存在 400、
 * viewer 写 403 读 200、对方单位关键字筛选。</p>
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
class PriceListTest {

    /** 供应商 Mapper。 */
    @Autowired
    private SupplierMapper supplierMapper;
    /** 客户 Mapper。 */
    @Autowired
    private CustomerMapper customerMapper;
    /** 物品 Mapper。 */
    @Autowired
    private ItemMapper itemMapper;
    /** JDBC(级联/状态断言)。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;
    /** REST 模板(真实 HTTP 断言)。 */
    @Autowired
    private TestRestTemplate rest;

    /** 价目测试供应商。 */
    private long supplierId;
    /** 价目测试供应商 2(关键字筛选用)。 */
    private long supplierId2;
    /** 价目测试客户。 */
    private long customerId;
    /** 价目测试物品 A。 */
    private long itemIdA;
    /** 价目测试物品 B。 */
    private long itemIdB;

    /**
     * 前置:RBAC 基线 + 自建供应商/客户/物品(seed 提供 admin/zhangsan/lisi 三个 HTTP 用户)。
     */
    @BeforeAll
    void cleanDb() {
        RbacSeedSupport.injectBaseline(jdbcTemplate);
        RbacSeedSupport.evictAuthCache(authCache);

        supplierId = insertSupplier("PL-SU1", "价目测试供应商一");
        supplierId2 = insertSupplier("PL-SU2", "价目测试供应商二");

        CustomerDO customer = new CustomerDO();
        customer.setCustomerCode("PL-CU1");
        customer.setCustomerName("价目测试客户");
        customer.setStatus(1);
        customerMapper.insert(customer);
        customerId = customer.getId();

        itemIdA = insertItem("PL-IT-A", "价目测试物A");
        itemIdB = insertItem("PL-IT-B", "价目测试物B");
    }

    /**
     * 每用例前置:清价目表两表(用例间隔离)。
     */
    @BeforeEach
    void resetLists() {
        jdbcTemplate.update("DELETE FROM price_list_item");
        jdbcTemplate.update("DELETE FROM price_list");
    }

    /**
     * 用例 1:CRUD——创建(name 空默认对方简称)→ 详情 → 编辑 → 删除级联删行。
     */
    @Test
    void crudAndCascadeDelete() {
        String admin = httpLogin("admin", "admin123");
        String create = "{\"ownerType\":\"supplier\",\"ownerId\":" + supplierId
                + ",\"validFrom\":\"2026-01-01\""
                + ",\"lines\":[{\"itemId\":" + itemIdA + ",\"unitPrice\":10,\"taxRate\":13}]}";
        Map<String, Object> created = postForMap(admin, "/api/v1/price-lists", create);
        long id = ((Number) created.get("id")).longValue();
        assertNotNull(id);
        assertEquals("价目测试供应商一", created.get("name"));
        assertEquals(1, ((Number) created.get("lineCount")).intValue());

        // 详情:行含物品回填
        Map<String, Object> detail = getForMap(admin, "/api/v1/price-lists/" + id);
        List<Map<String, Object>> lines = (List<Map<String, Object>>) detail.get("lines");
        assertEquals(1, lines.size());
        assertEquals("PL-IT-A", lines.get(0).get("itemCode"));

        // 编辑:改价 + 加一行
        String update = "{\"ownerType\":\"supplier\",\"ownerId\":" + supplierId
                + ",\"name\":\"改后名称\",\"validFrom\":\"2026-01-01\",\"validUntil\":\"2026-12-31\""
                + ",\"lines\":[{\"itemId\":" + itemIdA + ",\"unitPrice\":20},"
                + "{\"itemId\":" + itemIdB + ",\"unitPrice\":30,\"taxRate\":9}]}";
        Map<String, Object> updated = putForMap(admin, "/api/v1/price-lists/" + id, update);
        assertEquals("改后名称", updated.get("name"));
        assertEquals(2, ((Number) updated.get("lineCount")).intValue());

        // 删除:表头与行级联清空
        assertEquals(200, delete(admin, "/api/v1/price-lists/" + id).getStatusCode().value());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_list WHERE id = ?", Integer.class, id));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_list_item WHERE price_list_id = ?", Integer.class, id));
    }

    /**
     * 用例 2:effectivePrices 命中——单据日期在 [validFrom, validUntil] 内。
     */
    @Test
    void effectiveHitInWindow() {
        String admin = httpLogin("admin", "admin123");
        createList(admin, "supplier", supplierId, "2026-01-01", "2026-12-31",
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":10,\"taxRate\":13}");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) getForAny(admin,
                effectiveUrl("supplier", supplierId, LocalDate.now().toString()));
        assertEquals(1, rows.size());
        assertEquals(itemIdA, ((Number) rows.get(0).get("itemId")).longValue());
        assertEquals(0, new java.math.BigDecimal("10").compareTo(
                new java.math.BigDecimal(String.valueOf(rows.get(0).get("unitPrice")))));
    }

    /**
     * 用例 3:valid_until 可空(不限)时命中。
     */
    @Test
    void effectiveNullUntilHit() {
        String admin = httpLogin("admin", "admin123");
        createList(admin, "supplier", supplierId,
                LocalDate.now().minusDays(30).toString(), null,
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":12,\"taxRate\":13}");

        List<Map<String, Object>> rows = (List<Map<String, Object>>) getForAny(admin,
                effectiveUrl("supplier", supplierId, LocalDate.now().plusDays(30).toString()));
        assertEquals(1, rows.size());
    }

    /**
     * 用例 4:过期不命中(valid_until 早于查询日期)。
     */
    @Test
    void effectiveExpiredMiss() {
        String admin = httpLogin("admin", "admin123");
        createList(admin, "supplier", supplierId,
                LocalDate.now().minusDays(60).toString(),
                LocalDate.now().minusDays(1).toString(),
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":10,\"taxRate\":13}");

        List<Map<String, Object>> rows = (List<Map<String, Object>>) getForAny(admin,
                effectiveUrl("supplier", supplierId, LocalDate.now().toString()));
        assertTrue(rows.isEmpty(), "过期价目不应命中: " + rows);
    }

    /**
     * 用例 5:同物品多条命中取 valid_from 最近一条。
     */
    @Test
    void latestValidFromWins() {
        String admin = httpLogin("admin", "admin123");
        createList(admin, "supplier", supplierId,
                LocalDate.now().minusDays(60).toString(), null,
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":10,\"taxRate\":13}");
        createList(admin, "supplier", supplierId,
                LocalDate.now().minusDays(5).toString(), null,
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":99,\"taxRate\":13}");

        List<Map<String, Object>> rows = (List<Map<String, Object>>) getForAny(admin,
                effectiveUrl("supplier", supplierId, LocalDate.now().toString()));
        assertEquals(1, rows.size());
        assertEquals(0, new java.math.BigDecimal("99").compareTo(
                new java.math.BigDecimal(String.valueOf(rows.get(0).get("unitPrice")))));
    }

    /**
     * 用例 6:status=0 停用不命中。
     */
    @Test
    void disabledListMiss() {
        String admin = httpLogin("admin", "admin123");
        long id = createList(admin, "supplier", supplierId, "2026-01-01", null,
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":10,\"taxRate\":13}");
        jdbcTemplate.update("UPDATE price_list SET status = 0 WHERE id = ?", id);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) getForAny(admin,
                effectiveUrl("supplier", supplierId, LocalDate.now().toString()));
        assertTrue(rows.isEmpty(), "停用的价目不应命中: " + rows);
    }

    /**
     * 用例 7:价目行同物品重复 400。
     */
    @Test
    void duplicateLineRejected() {
        String admin = httpLogin("admin", "admin123");
        String body = "{\"ownerType\":\"supplier\",\"ownerId\":" + supplierId
                + ",\"lines\":[{\"itemId\":" + itemIdA + ",\"unitPrice\":10},"
                + "{\"itemId\":" + itemIdA + ",\"unitPrice\":20}]}";
        ResponseEntity<Map> res = post("/api/v1/price-lists", admin, body);
        assertEquals(400, res.getStatusCode().value());
        assertTrue(messageOf(res).contains("重复"), "应提示物品重复: " + messageOf(res));
    }

    /**
     * 用例 8:对方单位不存在 400。
     */
    @Test
    void ownerMissingRejected() {
        String admin = httpLogin("admin", "admin123");
        String body = "{\"ownerType\":\"supplier\",\"ownerId\":99999999"
                + ",\"lines\":[{\"itemId\":" + itemIdA + ",\"unitPrice\":10}]}";
        ResponseEntity<Map> res = post("/api/v1/price-lists", admin, body);
        assertEquals(400, res.getStatusCode().value());
        assertTrue(messageOf(res).contains("供应商不存在"), "应提示供应商不存在: " + messageOf(res));
    }

    /**
     * 用例 9:viewer 写 403 读 200。
     */
    @Test
    void viewerWriteForbiddenReadAllowed() {
        String viewer = httpLogin("lisi", "lisi123");
        String body = "{\"ownerType\":\"customer\",\"ownerId\":" + customerId
                + ",\"lines\":[{\"itemId\":" + itemIdA + ",\"unitPrice\":10}]}";
        assertEquals(403, post("/api/v1/price-lists", viewer, body).getStatusCode().value());
        assertEquals(200, get(viewer,
                "/api/v1/price-lists?ownerType=customer&page=1&pageSize=20")
                .getStatusCode().value());
    }

    /**
     * 用例 10:对方单位关键字筛选(编码/名称命中)。
     */
    @Test
    void listFilterByOwnerKeyword() {
        String admin = httpLogin("admin", "admin123");
        createList(admin, "supplier", supplierId, null, null,
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":10}");
        createList(admin, "supplier", supplierId2, null, null,
                "{\"itemId\":" + itemIdA + ",\"unitPrice\":20}");

        ResponseEntity<Map> res = get(admin,
                "/api/v1/price-lists?ownerType=supplier&ownerKeyword=供应商一&page=1&pageSize=20");
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals(1L, ((Number) body.get("total")).longValue());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        assertEquals("PL-SU1", rows.get(0).get("ownerCode"));
    }

    /**
     * 造供应商。
     *
     * @param code 编码
     * @param name 名称
     * @return 供应商 ID
     */
    private long insertSupplier(String code, String name) {
        SupplierDO supplier = new SupplierDO();
        supplier.setSupplierCode(code);
        supplier.setSupplierName(name);
        supplier.setStatus(1);
        supplierMapper.insert(supplier);
        return supplier.getId();
    }

    /**
     * 造物品。
     *
     * @param code 编码
     * @param name 名称
     * @return 物品 ID
     */
    private long insertItem(String code, String name) {
        ItemDO item = new ItemDO();
        item.setItemCode(code);
        item.setItemName(name);
        item.setUnit("件");
        itemMapper.insert(item);
        return item.getId();
    }

    /**
     * HTTP 创建价目表并断言 200。
     *
     * @param token       token
     * @param ownerType   对方类型
     * @param ownerId     对方 ID
     * @param validFrom   生效起(yyyy-MM-dd,可空)
     * @param validUntil  生效止(yyyy-MM-dd,可空)
     * @param lineJson    单行 JSON
     * @return 价目表 ID
     */
    private long createList(String token, String ownerType, long ownerId,
            String validFrom, String validUntil, String lineJson) {
        String body = "{\"ownerType\":\"" + ownerType + "\",\"ownerId\":" + ownerId
                + (validFrom == null ? "" : ",\"validFrom\":\"" + validFrom + "\"")
                + (validUntil == null ? "" : ",\"validUntil\":\"" + validUntil + "\"")
                + ",\"lines\":[" + lineJson + "]}";
        Map<String, Object> created = postForMap(token, "/api/v1/price-lists", body);
        return ((Number) created.get("id")).longValue();
    }

    /**
     * 生效价目 URL。
     *
     * @param ownerType 对方类型
     * @param ownerId   对方 ID
     * @param date      日期(yyyy-MM-dd)
     * @return URL
     */
    private String effectiveUrl(String ownerType, long ownerId, String date) {
        return "/api/v1/price-lists/effective?ownerType=" + ownerType
                + "&ownerId=" + ownerId + "&date=" + date;
    }

    /**
     * JSON POST(不断言状态码)。
     *
     * @param path  路径
     * @param token token
     * @param body  请求体
     * @return 响应
     */
    private ResponseEntity<Map> post(String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * JSON POST(断言 200 并取 body)。
     *
     * @param path  路径
     * @param token token
     * @param body  请求体
     * @return body
     */
    private Map<String, Object> postForMap(String token, String path, String body) {
        ResponseEntity<Map> res = post(path, token, body);
        assertEquals(200, res.getStatusCode().value(), "POST " + path + " 应 200: " + messageOf(res));
        return res.getBody();
    }

    /**
     * JSON PUT(断言 200 并取 body)。
     *
     * @param token token
     * @param path  路径
     * @param body  请求体
     * @return body
     */
    private Map<String, Object> putForMap(String token, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value(), "PUT " + path + " 应 200: " + messageOf(res));
        return res.getBody();
    }

    /**
     * JSON DELETE。
     *
     * @param token token
     * @param path  路径
     * @return 响应
     */
    private ResponseEntity<Map> delete(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }

    /**
     * JSON GET(不断言状态码)。
     *
     * @param token token
     * @param path  路径
     * @return 响应
     */
    private ResponseEntity<Map> get(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    /**
     * JSON GET(断言 200 并取 body,支持返回 Map 或 List 的端点)。
     *
     * @param token token
     * @param path  路径
     * @return body
     */
    @SuppressWarnings("unchecked")
    private Object getForAny(String token, String path) {
        ResponseEntity<Object> res = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Object.class);
        assertEquals(200, res.getStatusCode().value(), "GET " + path + " 应 200: " + res.getBody());
        return res.getBody();
    }

    /**
     * JSON GET(断言 200 并取 body)。
     *
     * @param token token
     * @param path  路径
     * @return body
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getForMap(String token, String path) {
        ResponseEntity<Map> res = get(token, path);
        assertEquals(200, res.getStatusCode().value(), "GET " + path + " 应 200: " + messageOf(res));
        return res.getBody();
    }

    /**
     * 带 Bearer 头的请求头。
     *
     * @param token token
     * @return 请求头
     */
    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * 取错误体 message。
     *
     * @param res 响应
     * @return message(可空)
     */
    private String messageOf(ResponseEntity<Map> res) {
        Map<String, Object> body = res.getBody();
        return body == null ? "" : String.valueOf(body.get("message"));
    }

    /**
     * 登录并返回 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return token
     */
    @SuppressWarnings("unchecked")
    private String httpLogin(String username, String password) {
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
