package com.company.inventory.rbac;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.model.entity.rbac.MenuDO;
import com.company.inventory.model.entity.rbac.RoleDO;
import com.company.inventory.model.entity.rbac.UserRoleDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.rbac.MenuMapper;
import com.company.inventory.mapper.rbac.RoleMapper;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.support.RbacSeedSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 核心测试:菜单树 API(三型/并集)、角色 CRUD(内置保护/用户绑定保护)、
 * 菜单 CRUD(code 唯一/子节点保护/删除连带解绑)、@RequirePerm 鉴权、
 * 旧版单 role claim token 兼容。自包含:自建数据自清理(TRUNCATE RBAC 5 表 + sys_user)。
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
class RbacTest {

    /** 测试密码明文。 */
    private static final String PW = "rbac123";

    /** 开发环境 JWT secret(与 application.yml 默认值一致,旧版 token 用例签发用)。 */
    private static final String JWT_SECRET =
            "dev-secret-please-change-in-production-9f3a8b2c1e4d";

    /** REST 客户端。 */
    @Autowired
    private TestRestTemplate rest;
    /** JDBC。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 权限缓存(测试前清缓存防串号)。 */
    @Autowired
    private AuthCache authCache;
    /** 角色 Mapper。 */
    @Autowired
    private RoleMapper roleMapper;
    /** 菜单 Mapper。 */
    @Autowired
    private MenuMapper menuMapper;
    /** 用户-角色 Mapper。 */
    @Autowired
    private UserRoleMapper userRoleMapper;
    /** 用户 Mapper。 */
    @Autowired
    private UserMapper userMapper;

    /** admin 角色 ID。 */
    private long roleIdAdmin;
    /** viewer 角色 ID。 */
    private long roleIdViewer;
    /** dualA 角色 ID(绑 menu-x)。 */
    private long roleIdDualA;
    /** dualB 角色 ID(绑 menu-y)。 */
    private long roleIdDualB;
    /** tester 角色 ID(非内置,删除用例用)。 */
    private long roleIdTester;
    /** bindrole 角色 ID(有用户绑定,禁删用例用)。 */
    private long roleIdBind;
    /** 目录 dir-a ID。 */
    private long menuIdDir;
    /** 菜单 menu-x ID。 */
    private long menuIdX;
    /** 菜单 menu-y ID。 */
    private long menuIdY;
    /** 按钮 purchase-order:approve ID。 */
    private long buttonIdApprove;

    /**
     * 前置:清 RBAC 5 表 + sys_user,造 6 角色 / 7 菜单 / 角色菜单绑定 / 4 用户。
     * (role:view / menu:view 按钮码供 RoleController / MenuController 类级鉴权)
     */
    @BeforeAll
    void setUp() {
        RbacSeedSupport.evictAuthCache(authCache);
        jdbcTemplate.execute("TRUNCATE sys_role_menu, sys_user_role, sys_user_warehouse,"
                + " sys_menu, sys_role, sys_user RESTART IDENTITY CASCADE");

        roleIdAdmin = insertRole("admin", "系统管理员", true);
        roleIdViewer = insertRole("viewer", "查看员", true);
        roleIdDualA = insertRole("dual_a", "双角色甲", false);
        roleIdDualB = insertRole("dual_b", "双角色乙", false);
        roleIdTester = insertRole("tester", "测试员", false);
        roleIdBind = insertRole("bind_role", "待删保护", false);

        menuIdDir = insertMenu(0L, "dir-a", "目录A", "directory", null, 1);
        menuIdX = insertMenu(menuIdDir, "menu-x", "菜单X", "menu", "/x", 1);
        menuIdY = insertMenu(menuIdDir, "menu-y", "菜单Y", "menu", "/y", 2);
        buttonIdApprove = insertMenu(menuIdX, "purchase-order:approve", "菜单X-审批", "button", null, 1);
        insertMenu(menuIdY, "item:edit", "菜单Y-编辑", "button", null, 1);
        // 角色/菜单管理页查看码(admin 绑定;供 /roles 与 /menus 类级鉴权)
        long buttonRoleView = insertMenu(menuIdY, "role:view", "角色-查看", "button", null, 2);
        long buttonMenuView = insertMenu(menuIdY, "menu:view", "菜单-查看", "button", null, 3);

        // admin 全量;viewer 仅目录+菜单;dualA→menu-x;dualB→menu-y
        bindMenus(roleIdAdmin, List.of(menuIdDir, menuIdX, menuIdY, buttonIdApprove,
                buttonRoleView, buttonMenuView));
        bindMenus(roleIdViewer, List.of(menuIdDir, menuIdX, menuIdY));
        bindMenus(roleIdDualA, List.of(menuIdX));
        bindMenus(roleIdDualB, List.of(menuIdY));
        bindMenus(roleIdTester, List.of(buttonIdApprove));

        insertUser("rbac_admin", "admin", roleIdAdmin);
        insertUser("rbac_viewer", "viewer", roleIdViewer);
        // 双角色用户(并集用例)+ 绑定 bindrole 的用户(禁删用例)
        insertUser("rbac_dual", "dual_a", roleIdDualA, roleIdDualB);
        insertUser("rbac_bound", "bind_role", roleIdBind);
        // 存量格式用户:仅 role 列,无 sys_user_role 绑定(登录回退兼容用例)
        insertLegacyUser("rbac_legacy", "tester");
    }

    /**
     * 收尾:清空 RBAC 5 表 + sys_user,避免其他测试类复用 sys_user 自增 id 时
     * 命中本类残留的 sys_user_role 绑定串号。
     */
    @AfterAll
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE sys_role_menu, sys_user_role, sys_user_warehouse,"
                + " sys_menu, sys_role, sys_user RESTART IDENTITY CASCADE");
    }

    /**
     * admin 菜单树:目录+菜单+按钮权限码折叠(菜单X 下含 purchase-order:approve)。
     */
    @Test
    void adminMenuTreeContainsButtonsAsPermissions() {
        String token = login("rbac_admin");
        List<Map<String, Object>> tree = getMenuTree(token);
        assertEquals(1, tree.size(), "admin 应只有 1 个顶级目录: " + tree);
        Map<String, Object> dir = tree.get(0);
        assertEquals("dir-a", dir.get("menuCode"));
        List<Map<String, Object>> children = (List<Map<String, Object>>) dir.get("children");
        assertEquals(2, children.size());
        Map<String, Object> menuX = children.get(0);
        assertEquals("menu-x", menuX.get("menuCode"));
        List<String> perms = (List<String>) menuX.get("permissions");
        assertEquals(List.of("purchase-order:approve"), perms);
        // 不应出现 button 节点本身
        for (Map<String, Object> c : children) {
            assertTrue(((List<?>) c.get("children")).isEmpty());
        }
    }

    /**
     * viewer 菜单树:有目录+菜单,但无任何按钮权限码。
     */
    @Test
    void viewerMenuTreeHasNoPermissions() {
        String token = login("rbac_viewer");
        List<Map<String, Object>> tree = getMenuTree(token);
        assertEquals(1, tree.size());
        List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get(0).get("children");
        assertEquals(2, children.size());
        for (Map<String, Object> c : children) {
            assertTrue(((List<?>) c.get("permissions")).isEmpty(),
                    "viewer 不应有按钮权限: " + c);
        }
    }

    /**
     * 双角色用户菜单树取并集:menu-x 与 menu-y 均可见。
     */
    @Test
    void dualRoleMenuTreeIsUnion() {
        String token = login("rbac_dual");
        List<Map<String, Object>> tree = getMenuTree(token);
        // dualA/dualB 只绑了菜单未绑目录 → 两个菜单按顶级展示,共 2 个根
        assertEquals(2, tree.size(), "双角色并集应有 menu-x 与 menu-y 两个根: " + tree);
        List<String> codes = tree.stream().map(m -> (String) m.get("menuCode")).toList();
        assertEquals(List.of("menu-x", "menu-y"), codes);
    }

    /**
     * 角色 CRUD:新建 + 列表 + 详情。
     */
    @Test
    void roleCrudBasic() {
        String token = login("rbac_admin");
        ResponseEntity<Map> created = post(token, "/api/v1/roles",
                "{\"roleCode\":\"crud_role\",\"roleName\":\"CRUD 角色\"}");
        assertEquals(200, created.getStatusCode().value());
        long newId = ((Number) created.getBody().get("id")).longValue();
        try {
            ResponseEntity<List> list = getList(token, "/api/v1/roles");
            assertEquals(200, list.getStatusCode().value());
            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM sys_role WHERE role_code = 'crud_role'", Integer.class) == 1);
            ResponseEntity<Map> detail = get(token, "/api/v1/roles/" + newId);
            assertEquals(200, detail.getStatusCode().value());
            assertEquals("crud_role", detail.getBody().get("roleCode"));
        } finally {
            assertEquals(200, delete(token, "/api/v1/roles/" + newId).getStatusCode().value());
        }
    }

    /**
     * 内置角色禁删。
     */
    @Test
    void builtinRoleDeleteForbidden() {
        String token = login("rbac_admin");
        ResponseEntity<Map> res = delete(token, "/api/v1/roles/" + roleIdAdmin);
        assertEquals(400, res.getStatusCode().value());
        assertTrue(String.valueOf(res.getBody().get("message")).contains("内置角色"));
    }

    /**
     * 有用户绑定的角色禁删。
     */
    @Test
    void boundRoleDeleteForbidden() {
        String token = login("rbac_admin");
        ResponseEntity<Map> res = delete(token, "/api/v1/roles/" + roleIdBind);
        assertEquals(400, res.getStatusCode().value());
        assertTrue(String.valueOf(res.getBody().get("message")).contains("绑定用户"));
    }

    /**
     * 非内置且无用户绑定的角色可删,role_menu 连带清理。
     */
    @Test
    void normalRoleDeleteCleansRoleMenu() {
        String token = login("rbac_admin");
        long bound = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_role_menu WHERE role_id = ?", Integer.class, roleIdTester);
        assertTrue(bound > 0, "前置:tester 角色应有菜单绑定");
        ResponseEntity<Map> res = delete(token, "/api/v1/roles/" + roleIdTester);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_role_menu WHERE role_id = ?", Integer.class, roleIdTester));
    }

    /**
     * 角色编码重复 → 400。
     */
    @Test
    void roleCodeDupRejected() {
        String token = login("rbac_admin");
        ResponseEntity<Map> res = post(token, "/api/v1/roles",
                "{\"roleCode\":\"admin\",\"roleName\":\"重复编码\"}");
        assertEquals(400, res.getStatusCode().value());
    }

    /**
     * 菜单编码重复 → 400。
     */
    @Test
    void menuCodeDupRejected() {
        String token = login("rbac_admin");
        ResponseEntity<Map> res = post(token, "/api/v1/menus",
                "{\"menuCode\":\"menu-x\",\"menuName\":\"重复\",\"type\":\"menu\"}");
        assertEquals(400, res.getStatusCode().value());
    }

    /**
     * 有子节点的菜单禁删。
     */
    @Test
    void menuWithChildrenDeleteForbidden() {
        String token = login("rbac_admin");
        ResponseEntity<Map> res = delete(token, "/api/v1/menus/" + menuIdDir);
        assertEquals(400, res.getStatusCode().value());
        assertTrue(String.valueOf(res.getBody().get("message")).contains("子节点"));
    }

    /**
     * 删菜单连带清 role_menu 绑定。
     */
    @Test
    void deleteMenuClearsRoleMenu() {
        String token = login("rbac_admin");
        long tmpMenu = insertMenu(0L, "tmp-menu", "临时菜单", "menu", "/tmp", 9);
        bindMenus(roleIdViewer, List.of(tmpMenu));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_role_menu WHERE menu_id = ? AND role_id = ?",
                Integer.class, tmpMenu, roleIdViewer));
        ResponseEntity<Map> res = delete(token, "/api/v1/menus/" + tmpMenu);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_role_menu WHERE menu_id = ?", Integer.class, tmpMenu));
    }

    /**
     * 角色-菜单全量替换:先绑 x 再绑 y,最终只剩 y。
     */
    @Test
    void assignMenusFullReplace() {
        String token = login("rbac_admin");
        long tmpRole = insertRole("replace_role", "替换角色", false);
        try {
            ResponseEntity<Map> put1 = put(token, "/api/v1/roles/" + tmpRole + "/menus",
                    "{\"menuIds\":[" + menuIdX + "]}");
            assertEquals(200, put1.getStatusCode().value());
            ResponseEntity<Map> put2 = put(token, "/api/v1/roles/" + tmpRole + "/menus",
                    "{\"menuIds\":[" + menuIdY + "]}");
            assertEquals(200, put2.getStatusCode().value());
            ResponseEntity<List> got = getList(token, "/api/v1/roles/" + tmpRole + "/menus");
            assertEquals(200, got.getStatusCode().value());
            assertEquals(1, got.getBody().size());
            assertEquals(menuIdY, ((Number) got.getBody().get(0)).longValue());
        } finally {
            jdbcTemplate.execute("DELETE FROM sys_user_role WHERE role_id = " + tmpRole);
            assertEquals(200, delete(token, "/api/v1/roles/" + tmpRole).getStatusCode().value());
        }
    }

    /**
     * @RequirePerm:有权限码 → 200;无权限码 → 403。
     */
    @Test
    void requirePermissionGate() {
        // rbac_admin 绑定含 purchase-order:approve 按钮 → 200
        String adminToken = login("rbac_admin");
        ResponseEntity<Map> ok = get(adminToken, "/api/v1/auth/perm-check");
        assertEquals(200, ok.getStatusCode().value());
        // rbac_viewer 无按钮绑定 → 403
        String viewerToken = login("rbac_viewer");
        ResponseEntity<Map> denied = get(viewerToken, "/api/v1/auth/perm-check");
        assertEquals(403, denied.getStatusCode().value());
    }

    /**
     * 存量格式用户(仅 role 列无 sys_user_role)登录回退兼容:roles 回退读 role 列。
     */
    @Test
    void legacyUserLoginFallsBackToRoleColumn() {
        ResponseEntity<Map> res = loginRaw("rbac_legacy");
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> user = (Map<String, Object>) res.getBody().get("user");
        assertEquals("tester", user.get("role"));
        assertEquals(List.of("tester"), user.get("roles"));
    }

    /**
     * 旧版单 role claim token(无 roles claim)仍可访问受 @RequirePerm 保护的接口。
     */
    @Test
    void legacyRoleClaimTokenStillWorks() {
        String legacyToken = Jwts.builder()
                .subject("1")
                .claim("username", "rbac_admin")
                .claim("role", "admin")
                .claim("name", "旧版用户")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        ResponseEntity<List> res = getList(legacyToken, "/api/v1/roles");
        assertEquals(200, res.getStatusCode().value());
        assertFalse(res.getBody() == null || res.getBody().isEmpty());
    }

    // ===== 辅助方法 =====

    /** 造角色。 */
    private long insertRole(String code, String name, boolean builtin) {
        RoleDO role = new RoleDO();
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setIsBuiltin(builtin);
        role.setStatus(1);
        roleMapper.insert(role);
        return role.getId();
    }

    /** 造菜单。 */
    private long insertMenu(long parentId, String code, String name, String type,
            String path, int sort) {
        MenuDO menu = new MenuDO();
        menu.setParentId(parentId);
        menu.setMenuCode(code);
        menu.setMenuName(name);
        menu.setType(type);
        menu.setPath(path);
        menu.setSort(sort);
        menu.setStatus(1);
        menuMapper.insert(menu);
        return menu.getId();
    }

    /** 角色-菜单绑定。 */
    private void bindMenus(long roleId, List<Long> menuIds) {
        for (Long menuId : menuIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)",
                    roleId, menuId);
        }
    }

    /** 造用户(写 sys_user_role 绑定,role 列同步写首个角色码兼容存量 NOT NULL)。 */
    private void insertUser(String username, String roleColumn, long... roleIds) {
        insertUserInternal(username, roleIds, roleColumn);
    }

    /** 造存量格式用户(仅 role 列,无 sys_user_role)。 */
    private void insertLegacyUser(String username, String role) {
        insertUserInternal(username, new long[0], role);
    }

    /** 造用户公共逻辑。 */
    private void insertUserInternal(String username, long[] roleIds, String legacyRole) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PW));
        user.setName(username);
        if (legacyRole != null) {
            user.setRole(legacyRole);
        }
        user.setStatus(1);
        userMapper.insert(user);
        for (long roleId : roleIds) {
            UserRoleDO binding = new UserRoleDO();
            binding.setUserId(user.getId());
            binding.setRoleId(roleId);
            userRoleMapper.insert(binding);
        }
    }

    /** 登录并返回 token。 */
    private String login(String username) {
        return loginRaw(username).getBody().get("token").toString();
    }

    /** 登录原始响应。 */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> loginRaw(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + PW + "\"}",
                        headers), Map.class);
        assertEquals(200, res.getStatusCode().value(), "登录应成功");
        return res;
    }

    /** 取当前用户菜单树。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMenuTree(String token) {
        ResponseEntity<List> res = getList(token, "/api/v1/auth/menus");
        assertEquals(200, res.getStatusCode().value());
        return (List<Map<String, Object>>) (List<?>) res.getBody();
    }

    private ResponseEntity<Map> get(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<List> getList(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), List.class);
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

    private ResponseEntity<Map> delete(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }
}
