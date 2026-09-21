package com.company.inventory.common.support;

import com.company.inventory.mapper.rbac.MenuMapper;
import com.company.inventory.mapper.rbac.UserWarehouseMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC 权限热点缓存(批 2):按钮权限码与数据权限仓库 ID 的 Redis 缓存。
 *
 * <p>key 规范(JSON 数组,值序列化后存储):
 * <ul>
 * <li>{@code auth:perm:{userId}} → 该用户按钮权限码列表(多角色并集),TTL 300 秒;</li>
 * <li>{@code auth:wh:{userId}} → 该用户授权仓库 ID 列表(数据权限过滤用),TTL 300 秒。</li>
 * </ul>
 * miss 回源 DB(原 SQL 逻辑),回源后写缓存;TTL 5 分钟为兜底,写路径变更时由
 * {@link #evictByPrefix()} 主动失效。</p>
 *
 * <p>{@link #evictByPrefix()} 按前缀 SCAN+DEL 全清,不按 userId 精确清:单机 3 用户
 * 规模,全清零漏失效(用户-角色/角色-菜单/用户-仓库任一变更都只清受影响用户,
 * 需维护完整依赖图,漏一条就是越权风险,全清最稳)。</p>
 *
 * <p>异常降级(fail-open):Redis 不可用时查缓存/回写视为未命中、直接回源 DB,
 * 失效清理只打 warn——极端情况下退化为每请求查库(缓存前旧行为),不阻断鉴权主链路。</p>
 *
 * <p>本类只依赖 RedisTemplate 与 Mapper,不依赖任何 service,与 {@code JwtInterceptor}
 * /service 层双向使用不形成循环依赖。</p>
 *
 * @author inventory
 */
@Component
public class AuthCache {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthCache.class);

    /** 权限码缓存 key 前缀。 */
    public static final String KEY_PREFIX_PERM = "auth:perm:";

    /** 仓库授权缓存 key 前缀。 */
    public static final String KEY_PREFIX_WH = "auth:wh:";

    /** 缓存 TTL:300 秒(写失效失效后的兜底窗口)。 */
    private static final Duration TTL = Duration.ofSeconds(300L);

    /** Redis 字符串模板。 */
    private final StringRedisTemplate redisTemplate;

    /** JSON 序列化器(列表值 JSON 数组化)。 */
    private final ObjectMapper objectMapper;

    /** 菜单 Mapper(权限码回源)。 */
    private final MenuMapper menuMapper;

    /** 用户-仓库 Mapper(数据权限回源)。 */
    private final UserWarehouseMapper userWarehouseMapper;

    /**
     * 构造缓存。
     *
     * @param redisTemplate       Redis 字符串模板
     * @param objectMapper        JSON 序列化器
     * @param menuMapper          菜单 Mapper
     * @param userWarehouseMapper 用户-仓库 Mapper
     */
    public AuthCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            MenuMapper menuMapper, UserWarehouseMapper userWarehouseMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.menuMapper = menuMapper;
        this.userWarehouseMapper = userWarehouseMapper;
    }

    /**
     * 查询用户按钮权限码集合(多角色并集,type='button' 且启用):先查 Redis,
     * miss 回源 DB 并写缓存(TTL 300 秒)。
     *
     * @param userId 用户 ID
     * @return 权限码集合(可能为空)
     */
    public Set<String> permissionCodes(long userId) {
        String key = KEY_PREFIX_PERM + userId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                List<String> codes = objectMapper.readValue(json,
                        new TypeReference<List<String>>() {
                        });
                return codes == null ? new HashSet<>() : new HashSet<>(codes);
            }
            List<String> codes = menuMapper.selectPermissionCodesByUserId(userId);
            List<String> safe = codes == null ? List.of() : codes;
            redisTemplate.opsForValue().set(key,
                    objectMapper.writeValueAsString(safe), TTL);
            return new HashSet<>(safe);
        } catch (Exception e) {
            // fail-open:Redis 故障直接回源 DB(缓存前旧行为)
            LOGGER.warn("权限码缓存 Redis 操作失败,回源 DB: {}", e.getMessage());
            List<String> codes = menuMapper.selectPermissionCodesByUserId(userId);
            return codes == null ? new HashSet<>() : new HashSet<>(codes);
        }
    }

    /**
     * 查询用户授权仓库 ID 列表(数据权限过滤用):先查 Redis,
     * miss 回源 DB 并写缓存(TTL 300 秒)。
     *
     * @param userId 用户 ID
     * @return 仓库 ID 列表(可能为空)
     */
    public List<Long> warehouseIds(long userId) {
        String key = KEY_PREFIX_WH + userId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                List<Long> ids = objectMapper.readValue(json,
                        new TypeReference<List<Long>>() {
                        });
                return ids == null ? List.of() : ids;
            }
            List<Long> ids = userWarehouseMapper.selectWarehouseIdsByUserId(userId);
            List<Long> safe = ids == null ? List.of() : ids;
            redisTemplate.opsForValue().set(key,
                    objectMapper.writeValueAsString(safe), TTL);
            return safe;
        } catch (Exception e) {
            // fail-open:Redis 故障直接回源 DB(缓存前旧行为)
            LOGGER.warn("仓库授权缓存 Redis 操作失败,回源 DB: {}", e.getMessage());
            List<Long> ids = userWarehouseMapper.selectWarehouseIdsByUserId(userId);
            return ids == null ? List.of() : ids;
        }
    }

    /**
     * 按前缀 SCAN+DEL 全清权限缓存(auth:perm:* 与 auth:wh:*)。
     *
     * <p>为什么不按 userId 精确清:用户-角色/角色-菜单/用户-仓库任一写路径变更,
     * 受影响用户集合需完整依赖图才能算准,漏一条 = 越权/越库风险;单机 3 用户,
     * 全清零漏、代价可忽略。</p>
     *
     * <p>调用时机:相关写接口落库事务成功后(先落库后清缓存;清理失败靠 TTL 兜底,
     * 最坏 5 分钟后自愈)。</p>
     */
    public void evictByPrefix() {
        try {
            redisTemplate.delete(scanKeys(KEY_PREFIX_PERM + "*"));
            redisTemplate.delete(scanKeys(KEY_PREFIX_WH + "*"));
        } catch (Exception e) {
            // 清理失败只告警:TTL 300 秒兜底自愈
            LOGGER.warn("权限缓存全清 Redis 失败,靠 TTL 兜底: {}", e.getMessage());
        }
    }

    /**
     * SCAN 出匹配模式的全部 key(不用 KEYS,避免阻塞)。
     *
     * @param pattern key 模式
     * @return key 列表(可能为空)
     */
    private List<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisConnection connection) -> {
            List<String> keys = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }
}
