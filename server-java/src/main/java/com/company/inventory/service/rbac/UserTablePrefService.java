package com.company.inventory.service.rbac;

import java.util.Map;

/**
 * 用户表格偏好服务:按 (用户, 页面) 存取 ProTable 列宽/显隐/列序配置,读路径带 Redis 缓存。
 *
 * @author inventory
 */
public interface UserTablePrefService {

    /**
     * 查询当前用户全部页面列配置(pageKey→config,config 为前端 {widths, hidden, order} 原文对象)。
     *
     * <p>先查 Redis 缓存,miss 回源 DB 后回写缓存(fail-open:Redis 不可用直接查 DB)。</p>
     *
     * @param userId 用户 ID
     * @return pageKey→config map(可能为空)
     */
    Map<String, Object> allForUser(long userId);

    /**
     * 保存(按 user_id + page_key upsert)指定页面的列配置,成功后失效该用户缓存。
     *
     * <p>config 直接存前端传来的 JSON 原文(Jackson 序列化进 jsonb 列),不做结构强校验。</p>
     *
     * @param userId  用户 ID
     * @param pageKey 页面标识(前端路由段)
     * @param config  前端列配置对象({widths, hidden, order})
     */
    void save(long userId, String pageKey, Object config);
}
