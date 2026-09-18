package com.company.inventory.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限码注解:标注在 Controller 方法(或类)上,声明访问所需 permission code。
 *
 * <p>用户权限码集合 = 当前用户所有角色的 sys_role_menu 绑定中 type='button' 的 menu_code
 * 并集(走 Redis 权限缓存,miss 回源 DB);与注解码集合有交集即放行。
 * 支持类级 + 方法级(方法注解优先)。</p>
 *
 * @author inventory
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /**
     * 需要的权限码数组,如 {"purchase-order:approve"}。
     *
     * @return 权限码列表
     */
    String[] value();
}
