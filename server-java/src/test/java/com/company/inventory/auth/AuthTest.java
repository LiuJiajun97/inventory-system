package com.company.inventory.auth;

import com.company.inventory.config.RequireRole;
import com.company.inventory.entity.user.UserDO;
import com.company.inventory.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;













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
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限测试(对照 server/test/auth.test.ts):
 * 1) viewer 调 POST /api/v1/inbound → 403 无权限;
 * 2) 无 token → 401 未登录。
 * 使用真实 HTTP(RANDOM_PORT),覆盖 JWT 拦截器与 @RequireRole 全链路。
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
class AuthTest {

    /** REST 客户端 */
    @Autowired
    private TestRestTemplate rest;
    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;
    /** 仓库 Mapper */
    @Autowired
    private WarehouseMapper warehouseMapper;
    /** JDBC 模板 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 前置:清库,造 viewer 用户与 1 个仓库。
     */
    @BeforeAll
    void cleanDb() {
        String sql = "TRUNCATE \"OutboundDocItem\",\"InboundDocItem\",\"OutboundDoc\",\"InboundDoc\","
                + "\"StockTransaction\",\"Stock\",\"Serial\",\"Batch\",\"Location\",\"Item\","
                + "\"Warehouse\",\"User\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        WarehouseDO wh = new WarehouseDO();
        wh.setWarehouseCode("AUTHW");
        wh.setWarehouseName("权限测试仓");
        wh.setWarehouseType("raw");
        warehouseMapper.insert(wh);

        UserDO user = new UserDO();
        user.setUsername("viewer1");
        user.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("viewer123"));
        user.setName("查看员");
        user.setRole("viewer");
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * viewer 调 POST /api/v1/inbound,403 无权限。
     */
    @Test
    void viewerInboundForbidden() {
        String token = login("viewer1", "viewer123");
        assertTrue(token != null && !token.isBlank(), "登录应返回 token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        String body = "{\"warehouseId\":1,\"items\":[{\"itemId\":1,\"qty\":1}]}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/inbound", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(403, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) res.getBody();
        assertTrue(String.valueOf(json.get("message")).contains("无权限"),
                "message 应含'无权限': " + json);
    }

    /**
     * 无 token 调 POST /api/v1/inbound,401 未登录。
     */
    @Test
    void noTokenUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"warehouseId\":1,\"items\":[{\"itemId\":1,\"qty\":1}]}";
        ResponseEntity<Map> res = rest.exchange("/api/v1/inbound", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertEquals(401, res.getStatusCode().value());
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
