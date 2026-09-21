package com.company.inventory.common.support;

/**
 * 用户上下文:ThreadLocal 持有当前请求的 username,供 MyBatis-Plus 自动填充读取。
 *
 * @author inventory
 */
public final class UserContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前用户。
     *
     * @param username 用户名
     */
    public static void set(String username) {
        HOLDER.set(username);
    }

    /**
     * 获取当前用户,查不到时返回 null。
     *
     * @return 用户名或 null
     */
    public static String get() {
        return HOLDER.get();
    }

    /**
     * 清除当前用户(防线程池串号)。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
