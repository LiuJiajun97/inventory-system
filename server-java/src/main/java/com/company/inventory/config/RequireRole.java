package com.company.inventory.config;






import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解:标注在 Controller 方法(或类)上,声明允许访问的角色。
 *
 * <p>权限矩阵与 Fastify 版各路由 preHandler requireRole 完全一致:
 * 入库/出库写操作 admin+operator;物品/仓库/库位/用户写操作 admin。</p>
 *
 * @author inventory
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 允许的角色值数组,如 {"admin", "operator"}。
     *
     * @return 角色值列表
     */
    String[] value();
}
