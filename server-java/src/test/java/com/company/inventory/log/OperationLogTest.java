package com.company.inventory.log;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.service.OperationLogService;
import com.company.inventory.support.RbacSeedSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V16 操作日志(审计日志)测试:
 * 1) 走 HTTP 建物品 → operation_log 多一条(username/module/action/success/ip 断言);
 * 2) 故意制造业务失败(重复编码 400)→ 记录 success=0、error_msg 非空;
 * 3) 列表分页契约 + viewer(非 admin)访问 403 + cleanup 只删保留期外数据。
 *
 * <p>自造数据自清:仅 TRUNCATE operation_log,用户/物品在 AfterAll 定向删除。</p>
 *
 * @author inventory
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123"
})
class OperationLogTest {

    /** 测试物品编码前缀(定向清理用)。 */
    private static final String ITEM_CODE = "OPLOG-IT-1";

    /** 清理探测记录用户名(唯一标记)。 */
    private static final String PROBE_USER = "cleanup_probe";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OperationLogService operationLogService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ItemMapper itemMapper;

    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;

    /**
     * 前置:注入 RBAC 基线 + 清权限缓存,清空操作日志 + 造 admin/viewer 用户(自包含)。
     */
    @BeforeAll
    void setUp() {
        RbacSeedSupport.injectBaseline(jdbcTemplate);
        RbacSeedSupport.evictAuthCache(authCache);
        jdbcTemplate.execute("TRUNCATE operation_log RESTART IDENTITY");
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(4);
        createUser("oplog_admin", "admin12345", "admin", enc);
        createUser("oplog_viewer", "viewer123", "viewer", enc);
        RbacSeedSupport.bindUserRole(jdbcTemplate, "oplog_admin");
        RbacSeedSupport.bindUserRole(jdbcTemplate, "oplog_viewer");
    }

    /**
     * 收尾:定向删除本类造的物品/用户,再清空操作日志(日志表只增不查业务,清空安全)。
     */
    @AfterAll
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM item WHERE item_code = ?", ITEM_CODE);
        RbacSeedSupport.unbindUser(jdbcTemplate, "oplog_admin");
        RbacSeedSupport.unbindUser(jdbcTemplate, "oplog_viewer");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN (?, ?)",
                "oplog_admin", "oplog_viewer");
        jdbcTemplate.execute("TRUNCATE operation_log RESTART IDENTITY");
    }

    /**
     * 用例 1:走 HTTP 建物品成功 → 日志多一条,字段断言。
     */
    @Test
    @Order(1)
    void createItemLogged() {
        String token = login("oplog_admin", "admin12345");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String body = "{\"itemCode\":\"" + ITEM_CODE + "\",\"itemName\":\"OPLOG 测试物品\","
                + "\"unit\":\"个\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/items", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value());

        // 断言切面产物:该用户该路径的 POST 日志
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, ip, module, action, path, success, error_msg, cost_ms"
                        + " FROM operation_log"
                        + " WHERE username = 'oplog_admin' AND path = '/api/v1/items'"
                        + " AND action = 'POST'");
        assertFalse(rows.isEmpty(), "建物品后 operation_log 应至少多 1 条");
        Map<String, Object> row = rows.get(0);
        assertEquals("物品", row.get("module"));
        assertEquals("POST", row.get("action"));
        assertEquals(1, ((Number) row.get("success")).intValue());
        assertNotNull(row.get("ip"), "ip 应非空");
        assertFalse(String.valueOf(row.get("ip")).isBlank());
        // 成功记录 error_msg 应为空
        assertEquals(null, row.get("error_msg"));
    }

    /**
     * 用例 2:故意制造业务失败(重复编码 400)→ success=0、error_msg 非空。
     */
    @Test
    @Order(2)
    void failedWriteLogged() {
        String token = login("oplog_admin", "admin12345");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String body = "{\"itemCode\":\"" + ITEM_CODE + "\",\"itemName\":\"OPLOG 重复编码\","
                + "\"unit\":\"个\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/items", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(400, res.getStatusCode().value());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT success, error_msg FROM operation_log"
                        + " WHERE username = 'oplog_admin' AND path = '/api/v1/items'"
                        + " AND action = 'POST' AND success = 0");
        assertFalse(rows.isEmpty(), "失败写操作应落 success=0 日志");
        Object msg = rows.get(0).get("error_msg");
        assertNotNull(msg, "失败日志 error_msg 应非空");
        assertFalse(String.valueOf(msg).isBlank());
    }

    /**
     * 用例 3:列表分页契约 + viewer 403 + cleanup 只删保留期外数据。
     */
    @Test
    @Order(3)
    void listContractViewerDeniedAndCleanup() {
        // 3.1 分页契约:admin 查列表,{rows,total,page,pageSize} 四字段
        String token = login("oplog_admin", "admin12345");
        ResponseEntity<Map> listRes = getWithToken("/api/v1/operation-logs?page=1&pageSize=10",
                token);
        assertEquals(200, listRes.getStatusCode().value());
        Map<String, Object> body = listRes.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("rows"));
        assertTrue(body.containsKey("total"));
        assertEquals(1, ((Number) body.get("page")).intValue());
        assertEquals(10, ((Number) body.get("pageSize")).intValue());
        assertTrue(((Number) body.get("total")).longValue() >= 2,
                "前两个用例已产生至少 2 条日志");

        // 3.2 viewer(非 admin)访问 → 403
        String viewerToken = login("oplog_viewer", "viewer123");
        ResponseEntity<Map> forbidden = getWithToken("/api/v1/operation-logs", viewerToken);
        assertEquals(403, forbidden.getStatusCode().value());

        // 3.3 cleanup(180):造 2 条探测记录(1 条 200 天前),清理后只剩新的一条
        jdbcTemplate.update(
                "INSERT INTO operation_log (username, ip, module, action, path, success,"
                        + " created_at) VALUES (?, '127.0.0.1', '物品', 'POST', '/probe', 1, now())",
                PROBE_USER);
        jdbcTemplate.update(
                "INSERT INTO operation_log (username, ip, module, action, path, success,"
                        + " created_at) VALUES (?, '127.0.0.1', '物品', 'POST', '/probe', 1,"
                        + " now() - interval '200 days')",
                PROBE_USER);
        int deleted = operationLogService.cleanup(180);
        assertTrue(deleted >= 1, "cleanup 应删掉 200 天前的探测记录,实删 " + deleted);
        Integer left = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE username = ?", Integer.class,
                PROBE_USER);
        assertEquals(1, left, "cleanup 后探测记录应剩 1 条(新记录保留)");
        // 清理探测记录
        jdbcTemplate.update("DELETE FROM operation_log WHERE username = ?", PROBE_USER);
    }

    /**
     * 造用户(sys_user.role 列,登录时多角色绑定为空则回退读该列)。
     *
     * @param username 用户名
     * @param pwd      明文密码
     * @param role     角色编码
     * @param enc      密码编码器
     */
    private void createUser(String username, String pwd, String role,
            BCryptPasswordEncoder enc) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(enc.encode(pwd));
        user.setName(username + "-显示名");
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 登录返回 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return JWT token
     */
    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> json = res.getBody();
        return json == null ? null : String.valueOf(json.get("token"));
    }

    /**
     * 带 token 的 GET。
     *
     * @param path  路径
     * @param token JWT token
     * @return 响应
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

}
