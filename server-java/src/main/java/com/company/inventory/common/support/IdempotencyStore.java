package com.company.inventory.common.support;

/**
 * 幂等结果缓存存储抽象(写接口标准 Idempotency-Key 语义)。
 *
 * <p>契约:同 key 的首个请求占位成功后执行,响应完成后把成功响应存入缓存;
 * 后续同 key 请求命中缓存则重放,命中"进行中"占位(无结果)则视为并发重复。</p>
 *
 * <p>当前为 Redis 实现 {@link RedisIdempotencyStore}(重启不丢状态);
 * 接口抽象使存储可替换,调用方无感知。</p>
 *
 * @author inventory
 */
public interface IdempotencyStore {

    /**
     * 为 key 抢占"进行中"占位(TTL 内已存在则抢占失败)。
     *
     * @param key       幂等键
     * @param ttlMillis 存活毫秒数
     * @return true = 抢占成功(本次请求是首个);false = key 已存在
     */
    boolean putIfAbsent(String key, long ttlMillis);

    /**
     * 把首次请求的成功响应写入缓存(与占位同 key)。
     *
     * @param key         幂等键
     * @param status      HTTP 状态码
     * @param contentType 响应 Content-Type(可为 null)
     * @param body        响应体字节
     * @param ttlMillis   结果缓存存活毫秒数
     */
    void putResult(String key, int status, String contentType, byte[] body, long ttlMillis);

    /**
     * 读取已成功完成的缓存结果。
     *
     * @param key 幂等键
     * @return 缓存结果;key 不存在或仍"进行中"返回 null
     */
    IdempotencyResult getResult(String key);

    /**
     * 释放 key(首次请求失败时调用,允许客户端同 key 重试)。
     *
     * @param key 幂等键
     */
    void evict(String key);

    /**
     * 清理全部过期条目。
     *
     * @return 清理的条目数
     */
    int evictExpired();
}
