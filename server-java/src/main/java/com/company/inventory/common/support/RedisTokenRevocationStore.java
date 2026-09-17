package com.company.inventory.common.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * JWT 吊销黑名单的 Redis 实现(重启不丢状态,登出失效跨重启保持)。
 *
 * <p>key 规范:{@code token:revoked:{jti}} → 字符串 "1",TTL = 该 token 剩余有效期
 * (签发时算好由调用方传入),token 自然过期后黑名单条目随之消失。</p>
 *
 * <p>异常降级(fail-open):Redis 不可用时拉黑失败只打 warn、查询视为未吊销——
 * 极端情况下已登出 token 在剩余有效期内仍可用,但保证 Redis 故障不打挂登录/鉴权
 * 主链路(单机自用,系统可用优先)。</p>
 *
 * @author inventory
 */
@Component
public class RedisTokenRevocationStore implements TokenRevocationStore {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTokenRevocationStore.class);

    /** 黑名单 key 前缀。 */
    private static final String KEY_PREFIX = "token:revoked:";

    /** Redis 字符串模板。 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 构造存储。
     *
     * @param redisTemplate Redis 字符串模板
     */
    public RedisTokenRevocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * @param jti        JWT id claim
     * @param ttlSeconds 拉黑存活秒数(token 剩余有效期)
     */
    @Override
    public void revoke(String jti, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1",
                    ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // fail-open:拉黑失败只告警,不阻断登出主链路
            LOGGER.warn("token 拉黑写 Redis 失败,登出后旧 token 可能仍有效: {}", e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param jti JWT id claim
     * @return true = 已吊销
     */
    @Override
    public boolean isRevoked(String jti) {
        try {
            return redisTemplate.hasKey(KEY_PREFIX + jti);
        } catch (Exception e) {
            // fail-open:查询失败视为未吊销,保证鉴权主链路可用
            LOGGER.warn("token 吊销查询 Redis 失败,视为未吊销: {}", e.getMessage());
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return 0(Redis 原生 TTL 过期,无需应用侧清理)
     */
    @Override
    public int evictExpired() {
        return 0;
    }
}
