package com.company.inventory.common.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 幂等结果缓存的 Redis 实现(重启不丢状态)。
 *
 * <p>key 规范(两类 key 分离,占位原子抢占天然并发正确):
 * <ul>
 * <li>占位 {@code idem:{key}}:SET NX EX(值为 "1"),抢占成功 = 首个请求;并发同 key
 * 抢不到占位且无结果 → 429 并发重复。</li>
 * <li>结果缓存 {@code idem:res:{key}}:值为 JSON {@code {status, contentType, body(base64)}},
 * EX 与占位同 TTL(5 分钟);同 key 后续请求命中则原样重放。</li>
 * </ul>
 * 过期清理由 Redis 原生 TTL 完成,无定时任务。</p>
 *
 * <p>异常降级(fail-open):Redis 不可用时占位视为抢占成功、结果读取视为未命中、
 * 写入/释放只打 warn 日志——极端情况下同 key 可能双写,与无幂等保护等价,
 * 但保证 Redis 故障不打挂下单主链路(单机轻量部署,系统可用优先)。</p>
 *
 * @author inventory
 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisIdempotencyStore.class);

    /** 占位 key 前缀。 */
    private static final String KEY_PLACEHOLDER = "idem:";

    /** 结果缓存 key 前缀。 */
    private static final String KEY_RESULT = "idem:res:";

    /** Redis 字符串模板。 */
    private final StringRedisTemplate redisTemplate;

    /** JSON 序列化器(结果缓存 value 用)。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造存储。
     *
     * @param redisTemplate Redis 字符串模板
     * @param objectMapper  JSON 序列化器
     */
    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * @param key       幂等键
     * @param ttlMillis 存活毫秒数
     * @return true = 抢占成功
     */
    @Override
    public boolean putIfAbsent(String key, long ttlMillis) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                    KEY_PLACEHOLDER + key, "1", ttlMillis, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // fail-open:Redis 故障时放行(与无幂等保护等价,不阻断下单主链路)
            LOGGER.warn("幂等占位写入 Redis 失败,放行: {}", e.getMessage());
            return true;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param key         幂等键
     * @param status      HTTP 状态码
     * @param contentType 响应 Content-Type(可为 null)
     * @param body        响应体字节
     * @param ttlMillis   结果缓存存活毫秒数
     */
    @Override
    public void putResult(String key, int status, String contentType, byte[] body, long ttlMillis) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", status);
            payload.put("contentType", contentType);
            payload.put("body", Base64.getEncoder().encodeToString(
                    body == null ? new byte[0] : body));
            redisTemplate.opsForValue().set(KEY_RESULT + key,
                    objectMapper.writeValueAsString(payload), ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // fail-open:结果缓存写失败只告警(占位仍在,同 key 重放能力降级为无缓存)
            LOGGER.warn("幂等结果写 Redis 失败: {}", e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param key 幂等键
     * @return 缓存结果;key 不存在或仍进行中返回 null
     */
    @Override
    public IdempotencyResult getResult(String key) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_RESULT + key);
            if (json == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);
            String bodyB64 = node.path("body").asText();
            byte[] body = bodyB64.isEmpty() ? new byte[0]
                    : Base64.getDecoder().decode(bodyB64);
            String contentType = node.path("contentType").asText(null);
            return new IdempotencyResult(node.path("status").asInt(), contentType, body);
        } catch (Exception e) {
            // fail-open:读取失败视为未命中(重放能力降级,请求继续执行)
            LOGGER.warn("幂等结果读 Redis 失败,视为未命中: {}", e.getMessage());
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param key 幂等键
     */
    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(java.util.List.of(KEY_PLACEHOLDER + key, KEY_RESULT + key));
        } catch (Exception e) {
            // fail-open:释放失败只告警(key 会随 TTL 自然过期)
            LOGGER.warn("幂等 key 释放 Redis 失败: {}", e.getMessage());
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
