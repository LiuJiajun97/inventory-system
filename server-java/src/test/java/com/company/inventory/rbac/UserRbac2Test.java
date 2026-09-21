package com.company.inventory.rbac;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.model.entity.rbac.UserRoleDO;
import com.company.inventory.model.entity.rbac.UserWarehouseDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import com.company.inventory.mapper.rbac.UserWarehouseMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.support.RbacSeedSupport;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 批 2 测试:用户多角色/仓库授权 API(sys_user_role / sys_user_warehouse upsert)+
 * 数据权限(7 列表按授权仓过滤,admin 豁免,未授权查空,调拨源仓或目的仓任一命中)。
 * 自包含:自建数据自清理(TRUNCATE RBAC 5 表 + 相关业务表,仅测试库)。
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
class UserRbac2Test {

    /** 测试密码明文。 */
    private static final String PW = "rbac2123";

    /** 业务表 + RBAC 表 TRUNCATE 语句(级联清外键)。 */
    private static final String TRUNCATE_SQL =
            "TRUNCATE warehouse, item, stock, stock_transaction, inbound_doc, inbound_doc_item,"
                    + " outbound_doc, outbound_doc_item, transfer_doc, transfer_doc_item,"
                    + " stocktake_doc, stocktake_doc_item, stock_adjust_doc, stock_adjust_doc_item,"
                    + " sys_role_menu, sys_user_role, sys_user_warehouse, sys_menu, sys_role, sys_user"
                    + " RESTART IDENTITY CASCADE";

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;
    /** JDBC。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;
    /** 用户-角色 Mapper。 */
    @Autowired
    private UserRoleMapper userRoleMapper;
    /** 用户-仓库 Mapper。 */
    @Autowired
    private UserWarehouseMapper userWarehouseMapper;

    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;

    /** admin 角色 ID。 */
    private long roleIdAdmin;
    /** operator 角色 ID。 */
    private long roleIdOperator;
    /** viewer 角色 ID。 */
    private long roleIdViewer;
    /** 仓库 A ID。 */
    private long whA;
    /** 仓库 B ID。 */
    private long whB;

    /**
     * 前置:清库,注入 RBAC 基线(3 内置角色 + 菜单 + 绑定),
     * 造 2 仓库 / 库存与 7 类单据数据 / 4 授权用户。
     */
    @BeforeAll
    void setUp() {
        RbacSeedSupport.evictAuthCache(authCache);
        jdbcTemplate.execute(TRUNCATE_SQL);
        // 菜单/角色基线(seed 幂等注入):内置 3 角色 id 与绑定一致,菜单 142 条含 user:view 等按钮码
        RbacSeedSupport.injectBaseline(jdbcTemplate);

        roleIdAdmin = roleCodeId("admin");
        roleIdOperator = roleCodeId("operator");
        roleIdViewer = roleCodeId("viewer");

        whA = insertWarehouse("R2-WH-A", "R2仓库A");
        whB = insertWarehouse("R2-WH-B", "R2仓库B");
        long itemId = insertItem("R2-IT", "R2测试物品");

        // 库存:仓 A 10 件,仓 B 5 件
        jdbcTemplate.update("INSERT INTO stock (warehouse_id, item_id, quantity, updated_at)"
                + " VALUES (?, ?, 10, now())", whA, itemId);
        jdbcTemplate.update("INSERT INTO stock (warehouse_id, item_id, quantity, updated_at)"
                + " VALUES (?, ?, 5, now())", whB, itemId);
        // 流水:两仓各 1 条
        jdbcTemplate.update("INSERT INTO stock_transaction (warehouse_id, item_id, change_qty,"
                + " after_qty, biz_code, doc_no, operator) VALUES (?, ?, 10, 10, 'INBOUND',"
                + " 'INB-R2-001', 'tester')", whA, itemId);
        jdbcTemplate.update("INSERT INTO stock_transaction (warehouse_id, item_id, change_qty,"
                + " after_qty, biz_code, doc_no, operator) VALUES (?, ?, 5, 5, 'INBOUND',"
                + " 'INB-R2-002', 'tester')", whB, itemId);
        // 入库单:两仓各 1 张(含单据行)
        long inA = insertDoc("INSERT INTO inbound_doc (doc_no, warehouse_id)"
                + " VALUES ('INB-R2-001', ?) RETURNING id", whA);
        jdbcTemplate.update("INSERT INTO inbound_doc_item (doc_id, item_id, quantity)"
                + " VALUES (?, ?, 10)", inA, itemId);
        long inB = insertDoc("INSERT INTO inbound_doc (doc_no, warehouse_id)"
                + " VALUES ('INB-R2-002', ?) RETURNING id", whB);
        jdbcTemplate.update("INSERT INTO inbound_doc_item (doc_id, item_id, quantity)"
                + " VALUES (?, ?, 5)", inB, itemId);
        // 出库单:仅仓 A 1 张
        long outA = insertDoc("INSERT INTO outbound_doc (doc_no, warehouse_id)"
                + " VALUES ('OUT-R2-001', ?) RETURNING id", whA);
        jdbcTemplate.update("INSERT INTO outbound_doc_item (doc_id, item_id, quantity)"
                + " VALUES (?, ?, 3)", outA, itemId);
        // 调拨单:A → B 1 张(源仓目的仓任一命中即可见)
        jdbcTemplate.update("INSERT INTO transfer_doc (doc_no, doc_date, from_warehouse_id,"
                + " to_warehouse_id, status) VALUES ('TRF-R2-001', now(), ?, ?, 'draft')",
                whA, whB);
        // 盘点单 / 调整单:仅仓 A 各 1 张
        jdbcTemplate.update("INSERT INTO stocktake_doc (doc_no, doc_date, warehouse_id, scope_type)"
                + " VALUES ('STK-R2-001', now(), ?, 'all')", whA);
        jdbcTemplate.update("INSERT INTO stock_adjust_doc (doc_no, doc_date, warehouse_id,"
                + " adjust_type) VALUES ('ADJ-R2-001', now(), ?, 'gain')", whA);

        // 授权用户:u1 授仓 A,u2 授仓 B,u3 未授权(数据权限用例)
        insertUser("r2_admin", List.of(roleIdAdmin), List.of());
        insertUser("r2_u1", List.of(roleIdOperator), List.of(whA));
        insertUser("r2_u2", List.of(roleIdOperator), List.of(whB));
        insertUser("r2_u3", List.of(roleIdViewer), List.of());
    }

    /**
     * 收尾:清空本类数据,避免污染其他测试类。
     */
    @AfterAll
    void cleanUp() {
        jdbcTemplate.execute(TRUNCATE_SQL);
    }

    /**
     * 用户多角色:创建 roleIds 生效 + 登录 roles 并集 + 更新 roleIds / warehouseIds 生效
     * (warehouseIds 空列表 = 清空授权;非法角色/仓库 ID 报 400)。
     */
    @Test
    void userMultiRoleAndWarehouseGrant() {
        String token = login("r2_admin");
        long userId = -1;
        try {
            // 多角色创建:operator + viewer
            ResponseEntity<Map> created = post(token, "/api/v1/users",
                    "{\"username\":\"r2_api\",\"password\":\"" + PW + "\",\"name\":\"接口用户\","
                            + "\"roleIds\":[" + roleIdOperator + "," + roleIdViewer + "]}");
            assertEquals(200, created.getStatusCode().value(), "多角色创建应成功: " + created.getBody());
            userId = ((Number) created.getBody().get("id")).longValue();
            assertEquals(2, ((List<?>) created.getBody().get("roles")).size(),
                    "VO 应返回 2 个角色: " + created.getBody());

            // 登录:roles 为并集
            Map<String, Object> user = loginUser("r2_api");
            List<String> roles = (List<String>) user.get("roles");
            assertTrue(roles.contains("operator") && roles.contains("viewer"),
                    "登录 roles 应为并集: " + roles);

            // 更新角色:缩到仅 operator
            ResponseEntity<Map> updRole = put(token, "/api/v1/users/" + userId,
                    "{\"roleIds\":[" + roleIdOperator + "]}");
            assertEquals(200, updRole.getStatusCode().value());
            assertEquals(1, ((List<?>) updRole.getBody().get("roles")).size());

            // 仓库授权:设仓 A
            ResponseEntity<Map> updWh = put(token, "/api/v1/users/" + userId,
                    "{\"warehouseIds\":[" + whA + "]}");
            assertEquals(200, updWh.getStatusCode().value());
            List<?> whList = (List<?>) updWh.getBody().get("warehouseIds");
            assertEquals(1, whList.size());
            assertEquals(whA, ((Number) whList.get(0)).longValue());

            // 仓库授权:空列表 = 清空
            ResponseEntity<Map> clrWh = put(token, "/api/v1/users/" + userId,
                    "{\"warehouseIds\":[]}");
            assertEquals(200, clrWh.getStatusCode().value());
            assertEquals(0, ((List<?>) clrWh.getBody().get("warehouseIds")).size());

            // 非法入参:空角色 / 不存在角色 / 不存在仓库
            assertEquals(400, put(token, "/api/v1/users/" + userId,
                    "{\"roleIds\":[]}").getStatusCode().value());
            assertEquals(400, put(token, "/api/v1/users/" + userId,
                    "{\"roleIds\":[999999]}").getStatusCode().value());
            assertEquals(400, put(token, "/api/v1/users/" + userId,
                    "{\"warehouseIds\":[999999]}").getStatusCode().value());
            ResponseEntity<Map> badCreate = post(token, "/api/v1/users",
                    "{\"username\":\"r2_bad\",\"password\":\"" + PW + "\",\"name\":\"坏角色\","
                            + "\"roleIds\":[999999]}");
            assertEquals(400, badCreate.getStatusCode().value());

            // 创建时直接带仓库授权(回归:曾漏写 sys_user_warehouse)
            ResponseEntity<Map> whCreate = post(token, "/api/v1/users",
                    "{\"username\":\"r2_whc\",\"password\":\"" + PW + "\",\"name\":\"创建授权用户\","
                            + "\"roleIds\":[" + roleIdOperator + "],\"warehouseIds\":[" + whB + "]}");
            assertEquals(200, whCreate.getStatusCode().value(), "创建带仓库授权应成功: " + whCreate.getBody());
            List<?> whcList = (List<?>) whCreate.getBody().get("warehouseIds");
            assertEquals(1, whcList.size());
            assertEquals(whB, ((Number) whcList.get(0)).longValue());
            jdbcTemplate.update("DELETE FROM sys_user_warehouse WHERE user_id = ?",
                    ((Number) whCreate.getBody().get("id")).longValue());
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?",
                    ((Number) whCreate.getBody().get("id")).longValue());
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?",
                    ((Number) whCreate.getBody().get("id")).longValue());
        } finally {
            if (userId > 0) {
                jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM sys_user_warehouse WHERE user_id = ?", userId);
                jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
            }
        }
    }

    /**
     * 数据权限 7 列表:u1(仓 A)/ u2(仓 B)/ u3(未授权)/ admin 各自的可见范围。
     */
    @Test
    void dataScopeFiltersSevenLists() {
        String tU1 = login("r2_u1");
        String tU2 = login("r2_u2");
        String tU3 = login("r2_u3");
        String tAdmin = login("r2_admin");

        // 库存:u1 只见仓 A 1 行,u2 只见仓 B 1 行,u3 查空,admin 全量 2 行
        assertListRows(tU1, "/api/v1/stock", 1, whA, "warehouseId");
        assertListRows(tU2, "/api/v1/stock", 1, whB, "warehouseId");
        assertListRows(tU3, "/api/v1/stock", 0, null, "warehouseId");
        assertListRows(tAdmin, "/api/v1/stock", 2, null, "warehouseId");

        // 流水:两仓各 1 条
        assertListRows(tU1, "/api/v1/transactions", 1, whA, "warehouseId");
        assertListRows(tU2, "/api/v1/transactions", 1, whB, "warehouseId");
        assertListRows(tU3, "/api/v1/transactions", 0, null, "warehouseId");
        assertListRows(tAdmin, "/api/v1/transactions", 2, null, "warehouseId");

        // 入库:两仓各 1 张
        assertListRows(tU1, "/api/v1/inbound", 1, whA, "warehouseId");
        assertListRows(tU2, "/api/v1/inbound", 1, whB, "warehouseId");
        assertListRows(tU3, "/api/v1/inbound", 0, null, "warehouseId");
        assertListRows(tAdmin, "/api/v1/inbound", 2, null, "warehouseId");

        // 出库:仅仓 A 1 张,u2 查空
        assertListRows(tU1, "/api/v1/outbound", 1, whA, "warehouseId");
        assertListRows(tU2, "/api/v1/outbound", 0, null, "warehouseId");
        assertListRows(tU3, "/api/v1/outbound", 0, null, "warehouseId");
        assertListRows(tAdmin, "/api/v1/outbound", 1, null, "warehouseId");

        // 盘点:仅仓 A 1 张
        assertListRows(tU1, "/api/v1/stocktakes", 1, whA, "warehouseId");
        assertListRows(tU2, "/api/v1/stocktakes", 0, null, "warehouseId");
        assertListRows(tU3, "/api/v1/stocktakes", 0, null, "warehouseId");
        assertListRows(tAdmin, "/api/v1/stocktakes", 1, null, "warehouseId");

        // 调整:仅仓 A 1 张
        assertListRows(tU1, "/api/v1/stock-adjusts", 1, whA, "warehouseId");
        assertListRows(tU2, "/api/v1/stock-adjusts", 0, null, "warehouseId");
        assertListRows(tU3, "/api/v1/stock-adjusts", 0, null, "warehouseId");
        assertListRows(tAdmin, "/api/v1/stock-adjusts", 1, null, "warehouseId");

        // 调拨:A → B 1 张,u1 源仓命中 / u2 目的仓命中 / u3 查空 / admin 全量
        assertListRows(tU1, "/api/v1/transfers", 1, null, null);
        assertListRows(tU2, "/api/v1/transfers", 1, null, null);
        assertListRows(tU3, "/api/v1/transfers", 0, null, null);
        assertListRows(tAdmin, "/api/v1/transfers", 1, null, null);
    }

    // ===== 辅助方法 =====

    /**
     * 断言列表接口的行数与(可选)所有行的仓库字段值。
     *
     * @param token         访问 token
     * @param path          列表接口路径
     * @param expectedTotal 期望总行数
     * @param expectWh      期望的仓库 ID(所有行一致),null 表示不校验
     * @param whField       仓库字段名(如 warehouseId),null 表示不校验
     */
    @SuppressWarnings("unchecked")
    private void assertListRows(String token, String path, long expectedTotal,
            Long expectWh, String whField) {
        ResponseEntity<Map> res = get(token, path + "?page=1&pageSize=50");
        assertEquals(200, res.getStatusCode().value(), path + " 应 200");
        Map<String, Object> body = res.getBody();
        assertEquals(expectedTotal, ((Number) body.get("total")).longValue(),
                path + " total 应为 " + expectedTotal + ": " + body.get("rows"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        assertEquals(rows.size(), expectedTotal);
        if (expectWh != null && whField != null) {
            for (Map<String, Object> row : rows) {
                assertEquals(expectWh, ((Number) row.get(whField)).longValue(),
                        path + " 行仓库应为 " + expectWh + ": " + row);
            }
        }
    }

    /** 造角色。 */
    /** 按 role_code 取内置角色 ID(seed 注入后存在)。 */
    private long roleCodeId(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ?", Long.class, code);
        if (id == null) {
            throw new IllegalStateException("seed 角色缺失: " + code);
        }
        return id;
    }

    /** 造仓库。 */
    private long insertWarehouse(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO warehouse (warehouse_code, warehouse_name, warehouse_type, status)"
                        + " VALUES (?, ?, 'raw', 1) RETURNING id",
                Long.class, code, name);
    }

    /** 造物品。 */
    private long insertItem(String code, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO item (item_code, item_name, unit, status) VALUES (?, ?, '件', 1)"
                        + " RETURNING id",
                Long.class, code, name);
    }

    /** 造单据主表(带 RETURNING id 的 INSERT 模板)。 */
    private long insertDoc(String sqlTemplate, long warehouseId) {
        return jdbcTemplate.queryForObject(sqlTemplate, Long.class, warehouseId);
    }

    /** 造用户(sys_user + sys_user_role + sys_user_warehouse)。 */
    private void insertUser(String username, List<Long> roleIds, List<Long> warehouseIds) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PW));
        user.setName(username);
        user.setRole("operator");
        user.setStatus(1);
        userMapper.insert(user);
        for (Long roleId : roleIds) {
            UserRoleDO binding = new UserRoleDO();
            binding.setUserId(user.getId());
            binding.setRoleId(roleId);
            userRoleMapper.insert(binding);
        }
        for (Long warehouseId : warehouseIds) {
            UserWarehouseDO grant = new UserWarehouseDO();
            grant.setUserId(user.getId());
            grant.setWarehouseId(warehouseId);
            userWarehouseMapper.insert(grant);
        }
    }

    /** 登录并返回 token。 */
    private String login(String username) {
        return loginRaw(username).getBody().get("token").toString();
    }

    /** 登录并返回 user 对象。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loginUser(String username) {
        ResponseEntity<Map> res = loginRaw(username);
        assertEquals(200, res.getStatusCode().value(), "登录应成功");
        return (Map<String, Object>) res.getBody().get("user");
    }

    /** 登录原始响应。 */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> loginRaw(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + PW + "\"}",
                        headers), Map.class);
    }

    private ResponseEntity<Map> get(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<Map> post(String token, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> put(String token, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);
    }
}
