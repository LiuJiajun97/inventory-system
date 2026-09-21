package com.company.inventory.service.rbac.impl;

import com.company.inventory.common.support.TablePrefCache;
import com.company.inventory.mapper.rbac.UserTablePrefMapper;
import com.company.inventory.model.entity.rbac.UserTablePrefDO;
import com.company.inventory.service.rbac.UserTablePrefService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户表格偏好服务实现:DB 为唯一事实源,Redis 只读缓存(TTL 300 秒,fail-open)。
 *
 * <p>读:{@link #allForUser(long)} 先查缓存,miss 回源 DB 并回写缓存。
 * 写:{@link #save(long, String, Object)} 先 upsert 落库,成功后 evict 该用户缓存
 * (清理失败靠 TTL 兜底,与 {@code AuthCache} 的失效约定一致)。</p>
 *
 * @author inventory
 */
@Service
public class UserTablePrefServiceImpl implements UserTablePrefService {

    /** 表格偏好 Mapper。 */
    private final UserTablePrefMapper tablePrefMapper;

    /** 表格偏好热点缓存(读缓存/回写/失效,fail-open)。 */
    private final TablePrefCache tablePrefCache;

    /** JSON 序列化器(config 原文进 jsonb 列 / 读出后转回对象)。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造服务。
     *
     * @param tablePrefMapper 表格偏好 Mapper
     * @param tablePrefCache  表格偏好热点缓存
     * @param objectMapper    JSON 序列化器
     */
    public UserTablePrefServiceImpl(UserTablePrefMapper tablePrefMapper,
            TablePrefCache tablePrefCache, ObjectMapper objectMapper) {
        this.tablePrefMapper = tablePrefMapper;
        this.tablePrefCache = tablePrefCache;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> allForUser(long userId) {
        // 缓存命中直接返回;miss(fail-open 时 Redis 不可用也走这里)回源 DB
        Map<String, Object> cached = tablePrefCache.get(userId);
        if (cached != null) {
            return cached;
        }
        List<UserTablePrefDO> rows = tablePrefMapper.selectList(
                new LambdaQueryWrapper<UserTablePrefDO>().eq(UserTablePrefDO::getUserId, userId));
        Map<String, Object> prefs = new LinkedHashMap<>();
        for (UserTablePrefDO row : rows) {
            prefs.put(row.getPageKey(), parseConfig(row.getConfig()));
        }
        tablePrefCache.put(userId, prefs);
        return prefs;
    }

    @Override
    @Transactional
    public void save(long userId, String pageKey, Object config) {
        String configJson = writeConfig(config);
        UserTablePrefDO existing = tablePrefMapper.selectOne(
                new LambdaQueryWrapper<UserTablePrefDO>()
                        .eq(UserTablePrefDO::getUserId, userId)
                        .eq(UserTablePrefDO::getPageKey, pageKey));
        if (existing == null) {
            UserTablePrefDO row = new UserTablePrefDO();
            row.setUserId(userId);
            row.setPageKey(pageKey);
            row.setConfig(configJson);
            tablePrefMapper.insert(row);
        } else {
            existing.setConfig(configJson);
            tablePrefMapper.updateById(existing);
        }
        // 先落库后清缓存;evict 失败靠 TTL 300 秒兜底
        tablePrefCache.evict(userId);
    }

    /**
     * 把 config 对象序列化为 JSON 原文存 jsonb 列(序列化失败抛运行时异常回滚)。
     *
     * @param config 前端列配置对象({widths, hidden, order})
     * @return JSON 字符串
     */
    private String writeConfig(Object config) {
        try {
            return objectMapper.writeValueAsString(config == null ? Map.of() : config);
        } catch (Exception e) {
            throw new IllegalStateException("表格偏好配置序列化失败", e);
        }
    }

    /**
     * 把 jsonb 列的 JSON 原文解析回对象;解析失败(历史脏数据)兜底返回空对象,不阻断整页配置读取。
     *
     * @param configJson JSON 原文
     * @return config 对象(解析失败为空对象)
     */
    private Object parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, LinkedHashMap.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
