package com.company.inventory.auth;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.support.RbacSeedSupport;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 登出失效测试:登录 token 含 jti;logout 后同 token 访问受保护接口 401;
 * 重新登录(新 jti)正常。自包含:自建用户,测试结束自清理。
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
class LogoutTest {

    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;

    /** JDBC 模板。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;

    /** JSON 序列化器(解析 token payload)。 */
    @Autowired
    private ObjectMapper objectMapper;

    /** 测试用户名/密码。 */
    private static final String USERNAME = "logout_user";
    private static final String PASSWORD = "logout123456";

    /**
     * 前置:清库,造测试用户。
     */
    @BeforeAll
    void setUp() {
        String sql = "TRUNCATE \"outbound_doc_item\",\"inbound_doc_item\",\"outbound_doc\",\"inbound_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\",\"location\",\"item\","
                + "\"warehouse\",\"sys_user\" RESTART IDENTITY CASCADE";
                RbacSeedSupport.injectBaseline(jdbcTemplate);
        RbacSeedSupport.evictAuthCache(authCache);
jdbcTemplate.execute(sql);

        UserDO user = new UserDO();
        user.setUsername(USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PASSWORD));
        user.setName("登出测试员");
        user.setRole("admin");
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 后置:清理测试用户。
     */
    @AfterAll
    void tearDown() {
        RbacSeedSupport.unbindUser(jdbcTemplate, USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", USERNAME);
    }

    /**
     * 登录 → token 含 jti;logout → 同 token 访问 /auth/me 401;重新登录正常。
     */
    @Test
    void logoutRevokesTokenAndReLoginWorks() throws Exception {
        String token = login(USERNAME, PASSWORD);
        assertTrue(token != null && !token.isBlank(), "登录应返回 token");

        // 1) 签发 token 含 jti
        String jti = decodeJti(token);
        assertTrue(jti != null && !jti.isBlank(), "token payload 应含 jti");

        // 2) 登出:拉黑当前 token 的 jti
        ResponseEntity<Map> logoutRes = authMe(token, HttpMethod.POST, "/auth/logout", null);
        assertEquals(200, logoutRes.getStatusCode().value(), "logout 应 200");
        @SuppressWarnings("unchecked")
        Map<String, Object> logoutBody = (Map<String, Object>) logoutRes.getBody();
        assertEquals(Boolean.TRUE, logoutBody.get("ok"), "logout 应返回 {ok:true}");

        // 3) 同 token 再访问受保护接口:401
        ResponseEntity<Map> revoked = authMe(token, HttpMethod.GET, "/auth/me", null);
        assertEquals(401, revoked.getStatusCode().value(), "登出后旧 token 应 401");

        // 4) 重新登录(新 jti):正常
        String newToken = login(USERNAME, PASSWORD);
        ResponseEntity<Map> relogin = authMe(newToken, HttpMethod.GET, "/auth/me", null);
        assertEquals(200, relogin.getStatusCode().value(), "重新登录后应正常访问");
    }

    /**
     * 带 Bearer token 访问 /auth 子路径。
     *
     * @param token    token
     * @param method   HTTP 方法
     * @param path     子路径(如 /auth/me)
     * @param body     请求体(可 null)
     * @return 响应
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> authMe(String token, HttpMethod method, String path,
            String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), Map.class);
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
        Map<String, Object> payload = objectMapper.readValue(
                new String(payloadBytes, StandardCharsets.UTF_8), Map.class);
        Object jti = payload.get("jti");
        return jti == null ? null : String.valueOf(jti);
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
