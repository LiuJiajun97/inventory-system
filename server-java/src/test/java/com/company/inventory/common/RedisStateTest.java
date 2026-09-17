package com.company.inventory.common;

import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批 1 状态持久化测试(幂等/登录锁定/登出黑名单换 Redis 实现):
 * 1) 登录锁定:override lock-seconds=1,错 5 次锁,等 1.2s 解锁;
 * 2) 幂等:同 key 重放一致 + 并发 429,并断言状态 key 真实落 Redis
 * (exists + TTL>0,证明状态在 Redis 而非 JVM,重启不丢);
 * 3) 登出:登出后 Redis 存在 token:revoked:{jti} 且旧 token 401。
 *
 * <p>自包含:PG 自建自清用户;Redis 用 6379 常驻实例(仅清本类 key 前缀,与本机开发 Redis
 * 不冲突),开始前按本类 key 前缀(idem:* / token:revoked:* / login:fail:*)清理,
 * 不用 FLUSHDB(避免影响共用实例的其他测试类)。</p>
 *
 * @author inventory
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123",
        "login.lock-seconds=1"
})
class RedisStateTest {

    /** 并发冲突观测的最大重试轮数(每次新 key,直到观测到 200+429 组合)。 */
    private static final int CONFLICT_MAX_ROUNDS = 10;

    /** 并发路数(同 key)。 */
    private static final int CONCURRENT_LANES = 4;

    /** 锁定窗口到期等待(毫秒),lock-seconds=1 时等 1.2s 足够。 */
    private static final long LOCK_WAIT_MILLIS = 1200L;

    /** 幂等测试用户名。 */
    private static final String IDEM_USERNAME = "redis_idem_admin";

    /** 登录锁定测试用户名。 */
    private static final String LOCK_USERNAME = "redis_lock_user";

    /** 登出测试用户名。 */
    private static final String LOGOUT_USERNAME = "redis_logout_user";

    /** 通用密码。 */
    private static final String PASSWORD = "redis123456";

    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;

    /** JDBC 模板。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;

    /** Redis 字符串模板(断言状态 key 真实落 Redis)。 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** JSON 序列化器(解析 token payload)。 */
    @Autowired
    private ObjectMapper objectMapper;

    /** admin token(幂等/登出用例用)。 */
    private String token;

    /**
     * 前置:清库,造三个测试用户,清理 Redis 本类前缀 key(自包含)。
     */
    @BeforeAll
    void setUp() throws Exception {
        String sql = "TRUNCATE \"outbound_doc_item\",\"inbound_doc_item\",\"outbound_doc\",\"inbound_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\",\"location\",\"item\","
                + "\"warehouse\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        insertUser(IDEM_USERNAME, "幂等持久化员", "admin");
        insertUser(LOCK_USERNAME, "锁定测试员", "operator");
        insertUser(LOGOUT_USERNAME, "登出测试员", "admin");

        // Redis 自包含:清本类 key 前缀(6379 常驻实例,只清本类 key 前缀,不用 FLUSHDB)
        redisTemplate.delete(keysByPattern("idem:*"));
        redisTemplate.delete(keysByPattern("idem:res:*"));
        redisTemplate.delete(keysByPattern("token:revoked:*"));
        redisTemplate.delete(keysByPattern("login:fail:*"));

        token = login(IDEM_USERNAME, PASSWORD);
        assertTrue(token != null && !token.isBlank(), "登录应返回 token");
    }

    /**
     * 后置:清理测试数据(物品 + 用户 + Redis 本类前缀 key)。
     */
    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM item WHERE item_code LIKE 'RDS_%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN (?, ?, ?)",
                IDEM_USERNAME, LOCK_USERNAME, LOGOUT_USERNAME);
        redisTemplate.delete(keysByPattern("idem:*"));
        redisTemplate.delete(keysByPattern("idem:res:*"));
        redisTemplate.delete(keysByPattern("token:revoked:*"));
        redisTemplate.delete(keysByPattern("login:fail:*"));
    }

    /**
     * 登录锁定:错 5 次进入锁定(锁定 key 落 Redis),锁定中正确密码也 429;
     * 等 1.2s 到期后正确密码可登录(计数重置)。
     */
    @Test
    void loginLockStateLivesInRedisAndUnlocks() throws InterruptedException {
        // 连续 5 次密码错误
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> res = doLogin(LOCK_USERNAME, "wrong-pass-" + i);
            assertEquals(401, res.getStatusCode().value(), "错误密码应 401");
        }
        // 锁定 key 真实落 Redis(TTL 300s)
        String lockKey = "login:fail:" + LOCK_USERNAME;
        assertTrue(redisTemplate.hasKey(lockKey), "锁定状态 key 应落 Redis");
        assertTrue(redisTemplate.getExpire(lockKey) > 0, "锁定 key 应有正 TTL");
        // 锁定中:正确密码也 429(auth_locked)
        ResponseEntity<Map> locked = doLogin(LOCK_USERNAME, PASSWORD);
        assertEquals(429, locked.getStatusCode().value(), "锁定中应 429");
        // 等锁定窗口(lock-seconds=1)过期
        Thread.sleep(LOCK_WAIT_MILLIS);
        // 到期后:正确密码可登录,且失败记录已清零
        ResponseEntity<Map> unlocked = doLogin(LOCK_USERNAME, PASSWORD);
        assertEquals(200, unlocked.getStatusCode().value(), "锁定到期后应可登录");
        assertFalse(redisTemplate.hasKey(lockKey), "登录成功后失败记录应清零");
    }

    /**
     * 幂等:同 key 重放一致,且占位/结果 key 真实落 Redis(状态在 Redis 而非 JVM)。
     */
    @Test
    void idempotencyStateLivesInRedis() {
        String itemCode = "RDS_R_" + System.nanoTime();
        String key = "rds-replay-" + itemCode;

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) postItems(itemJson(itemCode), key).getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) postItems(itemJson(itemCode), key).getBody();
        assertEquals(first, second, "同 key 第二次应原样重放第一次的响应体");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM item WHERE item_code = ?", Integer.class, itemCode);
        assertEquals(1, count, "同 key 重放不应产生重复数据");

        // 关键断言:状态真实落 Redis(重启不丢的前提)
        assertTrue(redisTemplate.hasKey("idem:res:" + key), "结果缓存 key 应落 Redis");
        assertTrue(redisTemplate.getExpire("idem:res:" + key) > 0, "结果缓存 key 应有正 TTL");
        assertTrue(redisTemplate.hasKey("idem:" + key), "占位 key 应落 Redis");
        assertTrue(redisTemplate.getExpire("idem:" + key) > 0, "占位 key 应有正 TTL");
    }

    /**
     * 幂等并发:同 key 4 路并发,并发窗口内抢不到占位的请求 429(idempotent_conflict)。
     */
    @Test
    void concurrentSameKeyYields429() {
        boolean observed = false;
        StringBuilder detail = new StringBuilder();
        for (int round = 0; round < CONFLICT_MAX_ROUNDS && !observed; round++) {
            String itemCode = "RDS_C_" + System.nanoTime() + "_" + round;
            String key = "rds-concurrent-" + itemCode;
            String body = itemJson(itemCode);
            List<CompletableFuture<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < CONCURRENT_LANES; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> postItems(body, key)
                        .getStatusCode().value()));
            }
            List<Integer> results = futures.stream().map(CompletableFuture::join).toList();
            detail.append("轮").append(round).append(": ").append(results).append(";");
            Set<Integer> statuses = new HashSet<>(results);
            if (statuses.contains(200) && statuses.contains(429)) {
                observed = true;
            } else {
                assertTrue(statuses.stream().allMatch(s -> s == 200),
                        "期望仅 200(重放)与 429(并发冲突),实际: " + results);
            }
        }
        assertTrue(observed, "多轮并发均未观测到 200+429 组合: " + detail);
    }

    /**
     * 登出:登出后 Redis 存在 token:revoked:{jti}(TTL=token 剩余有效期),旧 token 401。
     */
    @Test
    void logoutRevokesInRedis() throws Exception {
        String logoutToken = login(LOGOUT_USERNAME, PASSWORD);
        String jti = decodeJti(logoutToken);
        assertTrue(jti != null && !jti.isBlank(), "token payload 应含 jti");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(logoutToken);
        ResponseEntity<Map> logoutRes = rest.exchange("/api/v1/auth/logout",
                HttpMethod.POST, new HttpEntity<>(headers), Map.class);
        assertEquals(200, logoutRes.getStatusCode().value(), "logout 应 200");

        // 黑名单 key 落 Redis 且 TTL = token 剩余有效期(约 2h,远大于 0)
        String revokedKey = "token:revoked:" + jti;
        assertTrue(redisTemplate.hasKey(revokedKey), "登出后黑名单 key 应落 Redis");
        assertTrue(redisTemplate.getExpire(revokedKey) > 0, "黑名单 key 应有正 TTL");

        // 旧 token 再访问受保护接口 401
        headers.setBearerAuth(logoutToken);
        ResponseEntity<Map> revoked = rest.exchange("/api/v1/auth/me",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(401, revoked.getStatusCode().value(), "登出后旧 token 应 401");
    }

    /**
     * 建测试用户(BCrypt 强度 4 加速)。
     *
     * @param username 用户名
     * @param name     姓名
     * @param role     角色编码
     */
    private void insertUser(String username, String name, String role) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PASSWORD));
        user.setName(name);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 登录 /auth/login。
     *
     * @param username 用户名
     * @param password 密码
     * @return 响应
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> doLogin(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * 登录并取 token。
     *
     * @param username 用户名
     * @param password 密码
     * @return token
     */
    @SuppressWarnings("unchecked")
    private String login(String username, String password) throws Exception {
        ResponseEntity<Map> res = doLogin(username, password);
        assertEquals(200, res.getStatusCode().value(), "登录应 200");
        return (String) res.getBody().get("token");
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
        return "{\"itemCode\":\"" + itemCode + "\",\"itemName\":\"Redis 状态测试物品\","
                + "\"unit\":\"个\",\"spec\":\"S\"}";
    }

    /**
     * 解码 JWT payload 的 jti claim。
     *
     * @param token JWT 字符串
     * @return jti(无则 null)
     * @throws Exception JSON 解析异常
     */
    @SuppressWarnings("unchecked")
    private String decodeJti(String token) throws Exception {
        String payloadSegment = token.split("\\.")[1];
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadSegment);
        Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);
        Object jti = payload.get("jti");
        return jti == null ? null : String.valueOf(jti);
    }

    /**
     * SCAN 出匹配模式的全部 key(不用 KEYS,避免阻塞)。
     *
     * @param pattern key 模式
     * @return key 列表(可能为空)
     */
    private List<String> keysByPattern(String pattern) {
        return redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
            List<String> keys = new java.util.ArrayList<>();
            try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }
}
