package com.company.inventory.common.support;

/**
 * JWT 登出失效(token 吊销)存储抽象:按 jti 拉黑。
 *
 * <p>登出时以剩余 TTL 拉黑 jti;鉴权时验签通过后查黑名单,命中即 401。</p>
 *
 * <p>当前为 Redis 实现 {@link RedisTokenRevocationStore}(重启不丢状态);
 * 接口抽象使存储可替换,调用方无感知。</p>
 *
 * @author inventory
 */
public interface TokenRevocationStore {

    /**
     * 拉黑一个 jti。
     *
     * @param jti        JWT id claim
     * @param ttlSeconds 拉黑存活秒数(建议取 token 剩余有效期,到期自动失效)
     */
    void revoke(String jti, long ttlSeconds);

    /**
     * 查询 jti 是否已被拉黑。
     *
     * @param jti JWT id claim
     * @return true = 已吊销
     */
    boolean isRevoked(String jti);

    /**
     * 清理全部过期条目。
     *
     * @return 清理的条目数
     */
    int evictExpired();
}
