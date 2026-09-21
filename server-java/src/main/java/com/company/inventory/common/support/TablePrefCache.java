package com.company.inventory.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 表格偏好热点缓存:用户全部页面列配置(列宽/显隐/列序)的 Redis 缓存。
 *
 * <p>key 规范:{@code pref:table:{userId}} → 该用户全部 pageKey→config 的 JSON map,TTL 300 秒。
 * 读路径:{@link #get(long)} 命中直接返回,miss 返回 null 由调用方回源 DB 后经
 * {@link #put(long, Map)} 回写;写路径:DB upsert 成功后由 {@link #evict(long)} 主动失效。</p>
 *
 * <p>异常降级(fail-open):Redis 不可用时查缓存/回写视为未命中、直接回源 DB,
 * evict 只打 warn——退化为每请求查库,不阻断业务主链路。
 * 写法对齐 {@link AuthCache} 的 try/catch + Logger 约定。</p>
 *
 * @author inventory
 */
@Component
public class TablePrefCache {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(TablePrefCache.class);

    /** 表格偏好缓存 key 前缀。 */
    public static final String KEY_PREFIX = "pref:table:";

    /** 缓存 TTL:300 秒(写失效失效后的兜底窗口)。 */
    private static final Duration TTL = Duration.ofSeconds(300L);

    /** Redis 字符串模板。 */
    private final StringRedisTemplate redisTemplate;

    /** JSON 序列化器(pageKey→config map 序列化/反序列化)。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造缓存。
     *
     * @param redisTemplate Redis 字符串模板
     * @param objectMapper  JSON 序列化器
     */
    public TablePrefCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询用户全部页面列配置的缓存值。
     *
     * @param userId 用户 ID
     * @return pageKey→config map;未命中或 Redis 不可用(fail-open)时返回 null,由调用方回源 DB
     */
    public Map<String, Object> get(long userId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            if (json == null) {
                return null;
            }
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            // fail-open:Redis 故障视为未命中,由调用方直接回源 DB
            LOGGER.warn("表格偏好缓存 Redis 读取失败,回源 DB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 回写用户全部页面列配置到缓存(TTL 300 秒)。
     *
     * @param userId 用户 ID
     * @param prefs  pageKey→config map(可能为空)
     */
    public void put(long userId, Map<String, Object> prefs) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId,
                    objectMapper.writeValueAsString(prefs), TTL);
        } catch (Exception e) {
            // fail-open:回写失败只告警,下次读自然 miss 回源
            LOGGER.warn("表格偏好缓存 Redis 回写失败: {}", e.getMessage());
        }
    }

    /**
     * 失效用户列配置缓存(DB upsert 成功后调用)。
     *
     * @param userId 用户 ID
     */
    public void evict(long userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            // 清理失败只告警:TTL 300 秒兜底自愈
            LOGGER.warn("表格偏好缓存 Redis 失效失败,靠 TTL 兜底: {}", e.getMessage());
        }
    }
}
