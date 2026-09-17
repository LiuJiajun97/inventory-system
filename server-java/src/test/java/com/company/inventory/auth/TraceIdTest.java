package com.company.inventory.auth;

import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.UserMapper;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 链路追踪测试:
 * 1) 普通请求响应头 X-Trace-Id 非空且为 16 位 hex,两次请求不同;
 * 2) 带 W3C traceparent 的请求透传 trace-id 段;
 * 3) 404 请求也有 X-Trace-Id;
 * 4) 400 错误体含 traceId 字段。
 * 自包含:自建用户,测试结束自清理。
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
class TraceIdTest {

    /** W3C 测试用 trace-id(32 位 hex,非全 0)。 */
    private static final String W3C_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;

    /** JDBC 模板。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;

    /** admin token。 */
    private String token;

    /** 测试用户名。 */
    private static final String USERNAME = "trace_user";

    /**
     * 前置:清库,造 admin 用户并登录。
     */
    @BeforeAll
    void setUp() {
        String sql = "TRUNCATE \"outbound_doc_item\",\"inbound_doc_item\",\"outbound_doc\",\"inbound_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\",\"location\",\"item\","
                + "\"warehouse\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        UserDO user = new UserDO();
        user.setUsername(USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode("trace123456"));
        user.setName("追踪测试员");
        user.setRole("admin");
        user.setStatus(1);
        userMapper.insert(user);

        token = login(USERNAME, "trace123456");
        assertTrue(token != null && !token.isBlank(), "登录应返回 token");
    }

    /**
     * 后置:清理测试用户。
     */
    @org.junit.jupiter.api.AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", USERNAME);
    }

    /**
     * 普通请求:X-Trace-Id 非空 16 位 hex,两次请求不同。
     */
    @Test
    void normalRequestCarriesGeneratedTraceId() {
        ResponseEntity<String> first = getMe();
        ResponseEntity<String> second = getMe();
        assertEquals(200, first.getStatusCode().value());
        String traceId1 = first.getHeaders().getFirst("X-Trace-Id");
        String traceId2 = second.getHeaders().getFirst("X-Trace-Id");
        assertNotNull(traceId1, "响应头 X-Trace-Id 不应为空");
        assertTrue(traceId1.matches("[0-9a-f]{16}"), "生成的 traceId 应为 16 位 hex: " + traceId1);
        assertNotNull(traceId2);
        assertNotEquals(traceId1, traceId2, "两次请求 traceId 应不同");
    }

    /**
     * 带 W3C traceparent 的请求:X-Trace-Id 透传 trace-id 段。
     */
    @Test
    void w3cTraceParentIsPropagated() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("traceparent", "00-" + W3C_TRACE_ID + "-00f067aa0ba902b7-01");
        ResponseEntity<String> res =
                rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers),
                        String.class);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(W3C_TRACE_ID, res.getHeaders().getFirst("X-Trace-Id"),
                "应透传 traceparent 的 trace-id 段");
    }

    /**
     * 404 请求也有 X-Trace-Id(过滤器覆盖全部请求)。
     */
    @Test
    void notFoundStillCarriesTraceId() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> res = rest.exchange("/api/v1/nonexistent-route-xyz",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(404, res.getStatusCode().value());
        assertNotNull(res.getHeaders().getFirst("X-Trace-Id"), "404 响应也应有 X-Trace-Id");
    }

    /**
     * 400 错误体含 traceId 字段(与响应头一致)。
     */
    @Test
    void badRequestErrorBodyContainsTraceId() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("not-a-json", headers), Map.class);
        assertEquals(400, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        Object traceId = body.get("traceId");
        assertNotNull(traceId, "400 错误体应含 traceId: " + body);
        String headerTraceId = res.getHeaders().getFirst("X-Trace-Id");
        assertNotNull(headerTraceId);
        assertEquals(headerTraceId, String.valueOf(traceId), "错误体 traceId 应与响应头一致");
    }

    /**
     * 带 token GET /auth/me。
     *
     * @return 响应
     */
    private ResponseEntity<String> getMe() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers),
                String.class);
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
