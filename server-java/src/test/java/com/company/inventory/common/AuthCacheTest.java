package com.company.inventory.common;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.model.dto.rbac.MenuCreateDTO;
import com.company.inventory.model.dto.rbac.RoleCreateDTO;
import com.company.inventory.model.dto.user.UserCreateDTO;
import com.company.inventory.model.dto.user.UserUpdateDTO;
import com.company.inventory.model.dto.warehouse.WarehouseCreateDTO;
import com.company.inventory.model.vo.rbac.MenuNodeVO;
import com.company.inventory.model.vo.rbac.RoleVO;
import com.company.inventory.model.vo.user.UserVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;
import com.company.inventory.service.UserService;
import com.company.inventory.service.WarehouseService;
import com.company.inventory.service.rbac.MenuService;
import com.company.inventory.service.rbac.RoleService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批 2 权限缓存测试(AuthCache):
 * 1) 缓存命中:首次查询后 Redis 存在 auth:perm:{uid} key 且 TTL>0,连击第二次仍命中;
 * 2) 写失效:RoleServiceImpl.assignMenus 改角色菜单绑定后,权限码立即是新值(不等 TTL);
 * 3) 写失效:UserServiceImpl.update 换仓库授权后,warehouseIds 立即是新值(不等 TTL)。
 *
 * <p>自包含:自建仓库/角色/按钮菜单/用户,测试结束自清理;Redis 用 6379 常驻实例(开发 Redis,仅清本类 key 前缀)
 * 开始前/结束后按 auth:perm:* 与 auth:wh:* 前缀清理本类 key,不用 FLUSHDB
 * (避免影响共用实例的其他测试类)。</p>
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
class AuthCacheTest {

    /** 测试用户名。 */
    private static final String USERNAME = "cache_user";

    /** 测试角色编码。 */
    private static final String ROLE_CODE = "cache_role";

    /** 按钮菜单编码一(权限码 A)。 */
    private static final String PERM_A = "cache:perm-a";

    /** 按钮菜单编码二(权限码 B)。 */
    private static final String PERM_B = "cache:perm-b";

    /** 仓库服务。 */
    @Autowired
    private WarehouseService warehouseService;

    /** 角色服务。 */
    @Autowired
    private RoleService roleService;

    /** 菜单服务。 */
    @Autowired
    private MenuService menuService;

    /** 用户服务。 */
    @Autowired
    private UserService userService;

    /** 权限热点缓存(被测对象)。 */
    @Autowired
    private AuthCache authCache;

    /** Redis 字符串模板(断言缓存 key 落 Redis)。 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** JDBC 模板(清理 role_menu 等)。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 测试用户 ID。 */
    private Long userId;

    /** 测试角色 ID。 */
    private Long roleId;

    /** 按钮菜单 B1 ID(权限码 A)。 */
    private Long menuAId;

    /** 按钮菜单 B2 ID(权限码 B)。 */
    private Long menuBId;

    /** 仓库一 ID。 */
    private Long warehouseOneId;

    /** 仓库二 ID。 */
    private Long warehouseTwoId;

    /**
     * 前置:清残留,建仓库/角色/按钮菜单/用户(用户初始绑定角色 + 仓库一,
     * 角色初始绑定按钮 A),并清 Redis 本类前缀 key(自包含)。
     */
    @BeforeAll
    void setUp() {
        // 清上轮残留(测试中断时),按本类固定编码/用户名
        deleteOwnData();

        WarehouseVO w1 = warehouseService.create(new WarehouseCreateDTO(
                "CACHE_W1", "缓存测试仓一", "raw", false, false, false, false, false));
        WarehouseVO w2 = warehouseService.create(new WarehouseCreateDTO(
                "CACHE_W2", "缓存测试仓二", "raw", false, false, false, false, false));
        warehouseOneId = w1.id();
        warehouseTwoId = w2.id();

        RoleVO role = roleService.create(new RoleCreateDTO(ROLE_CODE, "缓存测试角色", null));
        roleId = role.id();

        MenuNodeVO menuA = menuService.create(new MenuCreateDTO(
                0L, PERM_A, "缓存按钮A", "button", null, 1));
        MenuNodeVO menuB = menuService.create(new MenuCreateDTO(
                0L, PERM_B, "缓存按钮B", "button", null, 2));
        menuAId = menuA.id();
        menuBId = menuB.id();

        UserVO user = userService.create(new UserCreateDTO(
                USERNAME, "cache123456", "缓存测试员", List.of(roleId), 1,
                List.of(warehouseOneId)));
        userId = user.id();

        // 角色初始绑定按钮 A(权限码 A)
        roleService.assignMenus(roleId, List.of(menuAId));

        // Redis 自包含:清本类 key 前缀(6379 常驻实例,只清本类 key 前缀,不用 FLUSHDB)
        redisTemplate.delete(keysByPattern(AuthCache.KEY_PREFIX_PERM + "*"));
        redisTemplate.delete(keysByPattern(AuthCache.KEY_PREFIX_WH + "*"));
    }

    /**
     * 后置:清理测试数据(关联表 → 主表)与 Redis 本类前缀 key。
     */
    @AfterAll
    void tearDown() {
        deleteOwnData();
        redisTemplate.delete(keysByPattern(AuthCache.KEY_PREFIX_PERM + "*"));
        redisTemplate.delete(keysByPattern(AuthCache.KEY_PREFIX_WH + "*"));
    }

    /**
     * 缓存命中:首次查询落 Redis(TTL 300s),连击第二次仍命中(key 存在且 TTL>0)。
     */
    @Test
    void permissionCodesCacheHit() {
        // 首次查询:回源 DB 并写缓存
        Set<String> first = authCache.permissionCodes(userId);
        assertEquals(Set.of(PERM_A), first, "初始权限码应为按钮 A");

        String permKey = AuthCache.KEY_PREFIX_PERM + userId;
        assertTrue(redisTemplate.hasKey(permKey), "首次查询后 Redis 应存在权限码缓存 key");
        long ttlAfterFirst = redisTemplate.getExpire(permKey);
        assertTrue(ttlAfterFirst > 0, "权限码缓存 key 应有正 TTL(300s 兜底)");

        // 连击第二次:命中缓存,key 仍在且 TTL>0
        Set<String> second = authCache.permissionCodes(userId);
        assertEquals(Set.of(PERM_A), second, "第二次查询应命中缓存且值一致");
        assertTrue(redisTemplate.getExpire(permKey) > 0, "第二次查询后 TTL 仍存在");
    }

    /**
     * 写失效:assignMenus 改角色菜单绑定后,权限码立即是新值(不等 TTL)。
     */
    @Test
    void assignMenusEvictsCacheImmediately() {
        // 预热缓存(此时权限码 = A)
        Set<String> before = authCache.permissionCodes(userId);
        assertEquals(Set.of(PERM_A), before, "改绑前权限码应为 A");
        assertTrue(redisTemplate.hasKey(AuthCache.KEY_PREFIX_PERM + userId),
                "预热后缓存 key 应存在");

        // 角色改绑按钮 B:落库成功后全清缓存
        roleService.assignMenus(roleId, List.of(menuBId));

        // 立即再查:回源读到新值 B(若缓存未失效,这里会读到旧值 A 而失败)
        Set<String> after = authCache.permissionCodes(userId);
        assertEquals(Set.of(PERM_B), after, "assignMenus 后权限码应立即是新值");
    }

    /**
     * 写失效:UserServiceImpl.update 换仓库授权后,warehouseIds 立即是新值(不等 TTL)。
     */
    @Test
    void userUpdateEvictsWarehouseCacheImmediately() {
        // 预热缓存(此时授权仓 = 仓库一)
        List<Long> before = authCache.warehouseIds(userId);
        assertEquals(List.of(warehouseOneId), before, "换仓前授权仓应为仓库一");
        assertTrue(redisTemplate.hasKey(AuthCache.KEY_PREFIX_WH + userId),
                "预热后缓存 key 应存在");

        // 换授权仓为仓库二
        userService.update(userId, new UserUpdateDTO(null, null,
                List.of(warehouseTwoId), null, null));

        // 立即再查:回源读到新值(若缓存未失效,这里会读到旧值而失败)
        List<Long> after = authCache.warehouseIds(userId);
        assertEquals(List.of(warehouseTwoId), after, "换仓后 warehouseIds 应立即是新值");
    }

    /**
     * 清理本类自建数据(关联表 → 主表,幂等可重复调用)。
     */
    private void deleteOwnData() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM sys_user_warehouse WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code = ?)", ROLE_CODE);
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code = ?", ROLE_CODE);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE menu_code IN (?, ?)",
                PERM_A, PERM_B);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", USERNAME);
        jdbcTemplate.update("DELETE FROM warehouse WHERE warehouse_code IN ('CACHE_W1','CACHE_W2')");
        // 字段置空,防重复前置时误用旧值
        userId = null;
        roleId = null;
        menuAId = null;
        menuBId = null;
        warehouseOneId = null;
        warehouseTwoId = null;
    }

    /**
     * SCAN 出匹配模式的全部 key(不用 KEYS,避免阻塞)。
     *
     * @param pattern key 模式
     * @return key 列表(可能为空)
     */
    private List<String> keysByPattern(String pattern) {
        return redisTemplate.execute(
                (org.springframework.data.redis.connection.RedisConnection connection) -> {
                    List<String> keys = new java.util.ArrayList<>();
                    try (org.springframework.data.redis.core.Cursor<byte[]> cursor =
                            connection.scan(org.springframework.data.redis.core.ScanOptions
                                    .scanOptions().match(pattern).count(100).build())) {
                        while (cursor.hasNext()) {
                            keys.add(new String(cursor.next(),
                                    java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                    return keys;
                });
    }
}
