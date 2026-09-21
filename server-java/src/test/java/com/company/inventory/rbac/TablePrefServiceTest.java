package com.company.inventory.rbac;

import com.company.inventory.common.support.TablePrefCache;
import com.company.inventory.mapper.rbac.UserTablePrefMapper;
import com.company.inventory.service.rbac.UserTablePrefService;
import com.company.inventory.service.rbac.impl.UserTablePrefServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表格偏好服务测试(UserTablePrefService + TablePrefCache):
 * 1) save 后 allForUser 取到,config 结构原样往返(JSON 对象);
 * 2) upsert 覆盖:同 user + page 二次 save 不新增行且取到最新值;
 * 3) 不同 pageKey 互不影响;
 * 4) 缓存命中与写失效:save 后 evict,绕过缓存直改 DB 再 allForUser 仍是旧值(证明命中),
 * save 一次后 allForUser 立即拿到新值(证明 evict 生效,不等 TTL);
 * 5) fail-open 降级:mock Redis 不可用(模板方法抛异常)时,get 返回 null(视为 miss),
 * put/evict 只告警不抛,服务仍走 DB 正常读写。
 *
 * <p>自包含:用专用测试用户 ID(不依赖 sys_user 行),测试前后清 DB 行与
 * pref:table:{userId} 缓存 key;Redis 用 6379 常驻实例,只清本类 key,不 FLUSHDB。</p>
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
class TablePrefServiceTest {

    /** 测试用户 ID(专用大值,避免与 sys_user 真实 ID 冲突;本表无外键约束)。 */
    private static final long TEST_USER_ID = 9_900_001L;

    /** 测试页面标识一。 */
    private static final String PAGE_A = "test-page-a";

    /** 测试页面标识二。 */
    private static final String PAGE_B = "test-page-b";

    /** 表格偏好服务(被测对象)。 */
    @Autowired
    private UserTablePrefService tablePrefService;

    /** 表格偏好 Mapper(fail-open 单测复用)。 */
    @Autowired
    private UserTablePrefMapper tablePrefMapper;

    /** JSON 序列化器(fail-open 单测复用)。 */
    @Autowired
    private ObjectMapper objectMapper;

    /** Redis 字符串模板(清理本类 key)。 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** JDBC 模板(清 DB 行 / 绕过缓存直改 config / 断言行数)。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 前置:清上轮残留(DB 行 + 缓存 key),保证自包含。
     */
    @BeforeAll
    void setUp() {
        cleanOwnData();
    }

    /**
     * 后置:清 DB 行 + 缓存 key,自清理。
     */
    @AfterAll
    void tearDown() {
        cleanOwnData();
    }

    /**
     * 清本测试用户的全部数据(DB 行 + pref:table 缓存 key)。
     */
    private void cleanOwnData() {
        jdbcTemplate.update("DELETE FROM sys_user_table_pref WHERE user_id = ?", TEST_USER_ID);
        redisTemplate.delete(TablePrefCache.KEY_PREFIX + TEST_USER_ID);
    }

    /**
     * 断言 config 里 widths.pageA 的宽度值(从 Object 树安全取值)。
     *
     * @param prefs pageKey→config map
     * @return widths.pageA 的 int 值(取不到时 -1)
     */
    @SuppressWarnings("unchecked")
    private int widthOf(Map<String, Object> prefs, String pageKey, String col) {
        Object config = prefs.get(pageKey);
        if (!(config instanceof Map)) {
            return -1;
        }
        Object widths = ((Map<String, Object>) config).get("widths");
        if (!(widths instanceof Map)) {
            return -1;
        }
        Object w = ((Map<String, Object>) widths).get(col);
        return w instanceof Number ? ((Number) w).intValue() : -1;
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveThenAllForUserReturnsConfig() {
        tablePrefService.save(TEST_USER_ID, PAGE_A,
                Map.of("widths", Map.of("pageA", 123), "hidden", java.util.List.of("colX")));

        Map<String, Object> prefs = tablePrefService.allForUser(TEST_USER_ID);
        assertTrue(prefs.containsKey(PAGE_A), "save 后 allForUser 应包含该 pageKey");
        assertEquals(123, widthOf(prefs, PAGE_A, "pageA"), "widths 值应原样往返");
        assertTrue(((Map<String, Object>) prefs.get(PAGE_A)).containsKey("hidden"),
                "config 顶层结构(hidden)应原样保留");
    }

    @Test
    void upsertOverwritesWithoutNewRow() {
        tablePrefService.save(TEST_USER_ID, PAGE_A, Map.of("widths", Map.of("pageA", 100)));
        tablePrefService.save(TEST_USER_ID, PAGE_A, Map.of("widths", Map.of("pageA", 200)));

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_user_table_pref WHERE user_id = ? AND page_key = ?",
                Integer.class, TEST_USER_ID, PAGE_A);
        assertEquals(1, rowCount, "同 user + page 二次 save 应 upsert 不新增行");

        Map<String, Object> prefs = tablePrefService.allForUser(TEST_USER_ID);
        assertEquals(200, widthOf(prefs, PAGE_A, "pageA"), "upsert 后应取到最新值");
    }

    @Test
    void differentPageKeysDoNotAffectEachOther() {
        tablePrefService.save(TEST_USER_ID, PAGE_A, Map.of("widths", Map.of("pageA", 111)));
        tablePrefService.save(TEST_USER_ID, PAGE_B, Map.of("widths", Map.of("pageB", 222)));

        Map<String, Object> prefs = tablePrefService.allForUser(TEST_USER_ID);
        assertEquals(111, widthOf(prefs, PAGE_A, "pageA"), "A 页配置不受 B 页影响");
        assertEquals(222, widthOf(prefs, PAGE_B, "pageB"), "B 页配置独立存在");

        // 再覆盖 A,B 应保持
        tablePrefService.save(TEST_USER_ID, PAGE_A, Map.of("widths", Map.of("pageA", 333)));
        Map<String, Object> prefs2 = tablePrefService.allForUser(TEST_USER_ID);
        assertEquals(333, widthOf(prefs2, PAGE_A, "pageA"), "覆盖 A 后 A 是新值");
        assertEquals(222, widthOf(prefs2, PAGE_B, "pageB"), "覆盖 A 后 B 保持原值");
    }

    @Test
    void cacheHitAndEvictOnSave() {
        tablePrefService.save(TEST_USER_ID, PAGE_A, Map.of("widths", Map.of("pageA", 100)));

        // 第一次 allForUser:miss 回源并回写缓存
        Map<String, Object> first = tablePrefService.allForUser(TEST_USER_ID);
        assertEquals(100, widthOf(first, PAGE_A, "pageA"), "回源应取到 DB 值");
        assertTrue(redisTemplate.hasKey(TablePrefCache.KEY_PREFIX + TEST_USER_ID),
                "miss 回源后应回写缓存 key");

        // 绕过缓存直改 DB:缓存命中时应仍返回旧值(证明读走缓存)
        jdbcTemplate.update(
                "UPDATE sys_user_table_pref SET config = ?::jsonb WHERE user_id = ? AND page_key = ?",
                "{\"widths\":{\"pageA\":999}}", TEST_USER_ID, PAGE_A);
        Map<String, Object> cached = tablePrefService.allForUser(TEST_USER_ID);
        assertNotEquals(999, widthOf(cached, PAGE_A, "pageA"), "缓存命中时不应看到 DB 直改的新值");

        // save 触发 evict:再 allForUser 应立即拿到新值(不等 TTL)
        tablePrefService.save(TEST_USER_ID, PAGE_A, Map.of("widths", Map.of("pageA", 400)));
        Map<String, Object> after = tablePrefService.allForUser(TEST_USER_ID);
        assertEquals(400, widthOf(after, PAGE_A, "pageA"), "evict 后应回源拿到最新值");
    }

    @Test
    void failOpenWhenRedisUnavailable() {
        // mock Redis 不可用:模板方法返回 null → 后续 NPE 全部被缓存层 catch(fail-open)
        StringRedisTemplate broken = Mockito.mock(StringRedisTemplate.class);
        TablePrefCache brokenCache = new TablePrefCache(broken, objectMapper);

        assertNull(brokenCache.get(TEST_USER_ID), "Redis 不可用时 get 应视为未命中(null)");
        // put / evict 不抛异常(只打 warn)
        brokenCache.put(TEST_USER_ID, Map.of(PAGE_A, Map.of()));
        brokenCache.evict(TEST_USER_ID);

        // 服务级:缓存恒 miss 时仍走 DB 正常读写
        UserTablePrefServiceImpl brokenService =
                new UserTablePrefServiceImpl(tablePrefMapper, brokenCache, objectMapper);
        brokenService.save(TEST_USER_ID, PAGE_B, Map.of("widths", Map.of("pageB", 555)));
        Map<String, Object> prefs = brokenService.allForUser(TEST_USER_ID);
        assertEquals(555, widthOf(prefs, PAGE_B, "pageB"), "Redis 不可用时应降级直查 DB");
    }
}
