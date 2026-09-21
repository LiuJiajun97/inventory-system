package com.company.inventory.common;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.model.entity.user.UserDO;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 写接口幂等测试(标准 Idempotency-Key,at-least-once 重试去重):
 * 1) 同 key 两次 POST /items → 第二次重放第一次响应,库里只多 1 条;
 * 2) 不带 key 正常创建;
 * 3) 同 key 并发双发 → 一个 200 一个 429(并发窗口内抢不到占位)。
 * 自包含:自建 admin 用户与物品,测试结束自清理。
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
class IdempotencyTest {

    /** 并发冲突观测的最大重试轮数(每次新 key,直到观测到 200+429 组合)。 */
    private static final int CONFLICT_MAX_ROUNDS = 10;

    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;

    /** JDBC 模板。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;

    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;

    /** admin token。 */
    private String token;

    /** 测试用户名。 */
    private static final String USERNAME = "idem_admin";

    /**
     * 前置:注入 RBAC 基线 + 清权限缓存,清库造 admin 用户并登录。
     */
    @BeforeAll
    void setUp() {
        RbacSeedSupport.injectBaseline(jdbcTemplate);
        RbacSeedSupport.evictAuthCache(authCache);
        String sql = "TRUNCATE \"outbound_doc_item\",\"inbound_doc_item\",\"outbound_doc\",\"inbound_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\",\"location\",\"item\","
                + "\"warehouse\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        UserDO user = new UserDO();
        user.setUsername(USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode("idem123456"));
        user.setName("幂等测试员");
        user.setRole("admin");
        user.setStatus(1);
        userMapper.insert(user);
        RbacSeedSupport.bindUserRole(jdbcTemplate, USERNAME);

        token = login(USERNAME, "idem123456");
        assertTrue(token != null && !token.isBlank(), "登录应返回 token");
    }

    /**
     * 后置:清理测试数据(物品 + 用户)。
     */
    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM item WHERE item_code LIKE 'IDEM_%'");
        RbacSeedSupport.unbindUser(jdbcTemplate, USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", USERNAME);
    }

    /**
     * 同 key 两次 POST /items:第二次重放第一次响应,库里只多 1 条。
     */
    @Test
    void sameKeyReplaysFirstResponse() {
        String itemCode = "IDEM_R_" + System.nanoTime();
        String key = "replay-key-" + itemCode;
        String body = itemJson(itemCode);

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) postItems(body, key).getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) postItems(body, key).getBody();

        assertEquals(first, second, "同 key 第二次应原样重放第一次的响应体");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM item WHERE item_code = ?", Integer.class, itemCode);
        assertEquals(1, count, "同 key 重放不应产生重复数据");
    }

    /**
     * 不带 Idempotency-Key:正常创建(向后兼容)。
     */
    @Test
    void noKeyCreatesNormally() {
        String itemCode = "IDEM_N_" + System.nanoTime();
        ResponseEntity<Map> res = postItems(itemJson(itemCode), null);
        assertEquals(200, res.getStatusCode().value());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM item WHERE item_code = ?", Integer.class, itemCode);
        assertEquals(1, count, "不带 key 应正常创建 1 条");
    }

    /**
     * 同 key 并发多路并发:并发窗口内抢不到占位的请求应 429(idempotent_conflict)。
     * 每轮 4 路同 key 并发(首请求完成后到达的为重放 200);逐轮换新 key,
     * 直到观测到 200+429 组合。
     */
    @Test
    void concurrentSameKeyYieldsOne429() {
        boolean observed = false;
        StringBuilder detail = new StringBuilder();
        for (int round = 0; round < CONFLICT_MAX_ROUNDS && !observed; round++) {
            String itemCode = "IDEM_C_" + System.nanoTime() + "_" + round;
            String key = "concurrent-key-" + itemCode;
            String body = itemJson(itemCode);
            List<CompletableFuture<String>> futures = List.of(
                    CompletableFuture.supplyAsync(() -> describePost(body, key)),
                    CompletableFuture.supplyAsync(() -> describePost(body, key)),
                    CompletableFuture.supplyAsync(() -> describePost(body, key)),
                    CompletableFuture.supplyAsync(() -> describePost(body, key)));
            List<String> results = futures.stream().map(CompletableFuture::join).toList();
            detail.append("轮").append(round).append(": ").append(results).append(";");
            Set<String> statuses = results.stream().map(r -> r.split(":")[0]).collect(Collectors.toSet());
            if (statuses.contains("200") && statuses.contains("429")) {
                observed = true;
            } else {
                // 200 重复 = 并发未重叠(重放),继续换 key;出现其他状态则直接失败
                assertTrue(statuses.stream().allMatch(s -> s.equals("200")),
                        "期望仅 200(重放)与 429(并发冲突),实际: " + results);
            }
        }
        assertTrue(observed, "多轮并发均未观测到 200+429 组合: " + detail);
    }

    /**
     * POST /items 并返回"状态码:body"描述串(并发诊断用)。
     *
     * @param body 请求体 JSON
     * @param key  幂等键
     * @return 状态码与响应体
     */
    @SuppressWarnings("unchecked")
    private String describePost(String body, String key) {
        ResponseEntity<Map> res = postItems(body, key);
        return res.getStatusCode().value() + ":" + res.getBody();
    }

    /**
     * POST /items(带 Bearer 与可选 Idempotency-Key)。
     *
     * @param body 请求体 JSON
     * @param key  幂等键(可 null)
     * @return 响应
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postItems(String body, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.exchange("/api/v1/items", HttpMethod.POST, new HttpEntity<>(body, headers),
                Map.class);
    }

    /**
     * 组物品创建请求体。
     *
     * @param itemCode 物品编码
     * @return JSON 字符串
     */
    private String itemJson(String itemCode) {
        return "{\"itemCode\":\"" + itemCode + "\",\"itemName\":\"幂等测试物品\","
                + "\"unit\":\"个\",\"spec\":\"S\"}";
    }

    /**
     * 登录并返回 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return token(失败返回 null)
     */
    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> json = (Map<String, Object>) res.getBody();
        return json == null ? null : String.valueOf(json.get("token"));
    }
}
