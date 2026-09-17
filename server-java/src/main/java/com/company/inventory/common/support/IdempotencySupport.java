package com.company.inventory.common.support;

import org.springframework.stereotype.Component;

/**
 * 幂等支撑门面:统一持有 TTL 参数并委托 {@link IdempotencyStore}。
 *
 * <p>拦截器只依赖本类,不直接依赖具体存储实现,便于存储替换(内存 → Redis)。</p>
 *
 * @author inventory
 */
@Component
public class IdempotencySupport {

    /** 幂等条目存活时间:5 分钟(覆盖客户端常见重试窗口)。 */
    public static final long TTL_MILLIS = 5 * 60 * 1000L;

    /** 幂等存储(当前为 Redis 实现,过期由 Redis 原生 TTL 完成,无应用侧定时清理)。 */
    private final IdempotencyStore store;

    /**
     * 构造门面。
     *
     * @param store 幂等存储
     */
    public IdempotencySupport(IdempotencyStore store) {
        this.store = store;
    }

    /**
     * 抢占幂等占位。
     *
     * @param key 幂等键
     * @return true = 抢占成功
     */
    public boolean putIfAbsent(String key) {
        return store.putIfAbsent(key, TTL_MILLIS);
    }

    /**
     * 写入成功响应缓存。
     *
     * @param key         幂等键
     * @param status      HTTP 状态码
     * @param contentType 响应 Content-Type(可为 null)
     * @param body        响应体字节
     */
    public void putResult(String key, int status, String contentType, byte[] body) {
        store.putResult(key, status, contentType, body, TTL_MILLIS);
    }

    /**
     * 读取成功响应缓存。
     *
     * @param key 幂等键
     * @return 缓存结果;不存在或进行中返回 null
     */
    public IdempotencyResult getResult(String key) {
        return store.getResult(key);
    }

    /**
     * 释放 key(首次请求失败,允许同 key 重试)。
     *
     * @param key 幂等键
     */
    public void evict(String key) {
        store.evict(key);
    }
}
