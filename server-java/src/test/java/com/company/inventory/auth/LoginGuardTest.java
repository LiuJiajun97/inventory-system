package com.company.inventory.auth;

import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.UserMapper;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录防爆破测试:连续 5 次失败锁 5 分钟(本测试 override 为 1 秒):
 * 锁定中(哪怕密码正确)一律 429 且消息带剩余秒数;锁定窗口内计数不继续累加;
 * 锁定到期记录自动重置,正确密码可立即登录。
 * 自包含:自建用户,测试结束自清理。
 *
 * @author inventory
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5433/inventory_test",
        "spring.datasource.username=inv",
        "spring.datasource.password=inv123",
        "login.lock-seconds=1"
})
class LoginGuardTest {

    /** 连续失败次数上限(与 application.yml 默认一致)。 */
    private static final int MAX_ATTEMPTS = 5;

    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;

    /** JDBC 模板。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;

    /** 测试用户名/密码。 */
    private static final String USERNAME = "lg_user";
    private static final String PASSWORD = "lg123456";

    /**
     * 前置:清库,造测试用户。
     */
    @BeforeAll
    void setUp() {
        String sql = "TRUNCATE \"outbound_doc_item\",\"inbound_doc_item\",\"outbound_doc\",\"inbound_doc\","
                + "\"stock_transaction\",\"stock\",\"serial\",\"batch\",\"location\",\"item\","
                + "\"warehouse\",\"sys_user\" RESTART IDENTITY CASCADE";
        jdbcTemplate.execute(sql);

        UserDO user = new UserDO();
        user.setUsername(USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PASSWORD));
        user.setName("防爆破测试员");
        user.setRole("admin");
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 第 1 步:错误密码连打 5 次,均为 401。
     */
    @Test
    @Order(1)
    void fiveFailuresReturn401() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            ResponseEntity<Map> res = login(USERNAME, "wrong-password");
            assertEquals(401, res.getStatusCode().value(),
                    "第 " + (i + 1) + " 次错误密码应 401");
        }
    }

    /**
     * 第 2 步:锁定窗口内,密码正确也 429,消息带剩余秒数;期间再试不再返回 401。
     */
    @Test
    @Order(2)
    void lockedEvenWithCorrectPassword() throws InterruptedException {
        ResponseEntity<Map> res = login(USERNAME, PASSWORD);
        assertEquals(429, res.getStatusCode().value(), "锁定中密码正确也应 429");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        String message = String.valueOf(body.get("message"));
        assertTrue(message.startsWith("登录失败次数过多,请") && message.endsWith("秒后重试"),
                "429 消息应含剩余秒数: " + message);

        // 锁定窗口内再失败 2 次:仍 429(计数锁定窗口内不继续累加,也不再走 401 通道)
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Map> locked = login(USERNAME, "wrong-password");
            assertEquals(429, locked.getStatusCode().value(), "锁定窗口内应持续 429");
        }

        // 等锁定到期(lock-seconds=1)
        Thread.sleep(1200L);
        // 到期后计数已重置:正确密码应立即登录成功(若窗口内继续累加,则还需再失败 4 次才能解锁)
        ResponseEntity<Map> after = login(USERNAME, PASSWORD);
        assertEquals(200, after.getStatusCode().value(), "锁定到期且计数重置后应能正常登录");
    }

    /**
     * 登录请求(真实 HTTP)。
     *
     * @param username 用户名
     * @param password 密码
     * @return 响应
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }
}
