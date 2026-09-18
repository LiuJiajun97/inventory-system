package com.company.inventory.support;

import com.company.inventory.common.support.AuthCache;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RBAC 测试基线支撑(测试基建):统一解决"测试库 RBAC 数据自包含 + 权限缓存串号"。
 *
 * <p>背景:接口鉴权已由旧版角色注解(读 JWT 角色)切换为 @RequirePerm(读 DB 权限码),
 * 权限码走 Redis 缓存 auth:perm:{userId}(TTL 300 秒)。测试库 sys_user 各测试类
 * TRUNCATE RESTART IDENTITY 会导致 userId 跨类复用,缓存残留会串号误判;
 * 且各测试类自建用户只写已废弃的 role 列,无 sys_user_role/sys_role_menu 绑定,
 * 权限码鉴权必然 403。</p>
 *
 * <p>约定:走真实 HTTP 的测试类在 @BeforeAll 里调用
 * {@link #injectBaseline(JdbcTemplate)} + {@link #evictAuthCache(AuthCache)},
 * 自建用户后调用 {@link #bindUserRole(JdbcTemplate, String)} 回填角色绑定。</p>
 *
 * @author inventory
 */
public final class RbacSeedSupport {

    /** 种子文件 classpath 位置(main resources,测试 classpath 可见)。 */
    private static final String SEED_PATH = "db/seed.sql";

    /** 全表清洗语句(RESTART IDENTITY 保证 id 与 seed 固定 id 行一致)。 */
    private static final String TRUNCATE_ALL_SQL =
            "DO $$ DECLARE r record; BEGIN "
                    + "FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP "
                    + "EXECUTE 'TRUNCATE public.' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE'; "
                    + "END LOOP; END $$;";

    private RbacSeedSupport() {
    }

    /**
     * 注入种子基线:先全表清洗(RESTART IDENTITY),再执行 seed.sql 全文。
     *
     * <p>全表清洗后注入可保证:① 固定 id 行(仓库 1-3 / 字典 / 菜单 1-142 / 角色 1-3)
     * 与空库一致,seed 原样落库(含 sys_user_role (1,1)(2,2)(3,3),与 seed 用户 id 1-3 一致);
     * ② 上一测试类残留不污染本类断言。调用方自建数据应在本方法之后执行。</p>
     *
     * @param jdbc JDBC 模板
     */
    public static void injectBaseline(JdbcTemplate jdbc) {
        jdbc.execute(TRUNCATE_ALL_SQL);
        ClassPathResource resource = new ClassPathResource(SEED_PATH);
        try (InputStream in = resource.getInputStream()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            jdbc.execute(sql);
        } catch (IOException e) {
            throw new IllegalStateException("读取 seed.sql 失败", e);
        }
    }

    /**
     * 清 Redis 权限/仓库授权缓存(auth:perm:* 与 auth:wh:*),
     * 防跨测试类 userId 复用导致的缓存串号。
     *
     * @param authCache 权限缓存组件
     */
    public static void evictAuthCache(AuthCache authCache) {
        authCache.evictByPrefix();
    }

    /**
     * 为"仅写了 role 列"的存量测试用户回填 sys_user_role 绑定
     * (role 列值按 role_code 匹配内置角色;与生产 seed 的用户-角色语义一致)。
     *
     * @param jdbc     JDBC 模板
     * @param username 用户名
     */
    public static void bindUserRole(JdbcTemplate jdbc, String username) {
        jdbc.update(
                "INSERT INTO sys_user_role (user_id, role_id) "
                        + "SELECT u.id, r.id FROM sys_user u "
                        + "JOIN sys_role r ON r.role_code = u.role "
                        + "WHERE u.username = ? "
                        + "ON CONFLICT (user_id, role_id) DO NOTHING",
                username);
    }

    /**
     * 解除用户角色/仓库授权绑定(删除测试用户前调用,防外键 fk_sys_user_role_user 阻挡)。
     *
     * @param jdbc     JDBC 模板
     * @param username 用户名
     */
    public static void unbindUser(JdbcTemplate jdbc, String username) {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = "
                + "(SELECT id FROM sys_user WHERE username = ?)", username);
        jdbc.update("DELETE FROM sys_user_warehouse WHERE user_id = "
                + "(SELECT id FROM sys_user WHERE username = ?)", username);
    }
}
