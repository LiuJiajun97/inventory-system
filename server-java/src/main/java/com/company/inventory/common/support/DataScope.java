package com.company.inventory.common.support;

import java.util.List;

/**
 * 数据权限上下文:ThreadLocal 持有当前请求的授权仓库 ID 列表,列表查询按此过滤。
 *
 * <p>语义:值 = null 表示豁免不过滤(admin);空列表表示未授权任何仓库(查空);
 * 非空列表表示仅可见这些仓库。由 JwtInterceptor 每请求设置、请求结束清理。</p>
 *
 * @author inventory
 */
public final class DataScope {

    private static final ThreadLocal<List<Long>> HOLDER = new ThreadLocal<>();

    private DataScope() {
    }

    /**
     * 设置当前请求的授权仓库 ID 列表(null = 豁免)。
     *
     * @param allowedWarehouseIds 授权仓库 ID 列表或 null
     */
    public static void set(List<Long> allowedWarehouseIds) {
        HOLDER.set(allowedWarehouseIds);
    }

    /**
     * 获取当前请求的授权仓库 ID 列表。
     *
     * @return null(豁免)/ 空列表(查空)/ 非空(仅查授权仓);未设置时为 null
     */
    public static List<Long> allowedWarehouseIds() {
        return HOLDER.get();
    }

    /**
     * 清除当前请求的数据权限(防线程池串号)。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
