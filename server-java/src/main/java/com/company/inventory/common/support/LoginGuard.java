package com.company.inventory.common.support;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录防爆破守卫(Redis 实现,重启不丢状态):按用户名累计连续失败次数,超限锁定一段时间。
 *
 * <p>key 规范:{@code login:fail:{username}} → Hash {@code {count, lockedUntil}}
 * (lockedUntil 为 epoch millis,0 = 未锁定),key TTL 固定 300 秒——锁定到期后
 * key 自然消失 = 计数重置,与内存版语义一致;TTL 应与 login.lock-seconds 保持一致
 * (默认配置下二者均为 300 秒,若 lock-seconds 调大于 300,锁定将在 key 过期时提前解除)。</p>
 *
 * <p>规则(参数走 application.yml:login.max-attempts / login.lock-seconds):
 * 连续 {@code maxAttempts} 次失败 → 锁定 {@code lockSeconds} 秒;锁定期间无论密码
 * 正确与否一律 429 拒绝,且失败计数不再累加;锁定到期记录自动清除(计数重置);
 * 登录成功立即清零。</p>
 *
 * <p>异常降级(fail-open):Redis 不可用时锁定检查放行、计数失败只打 warn——
 * 极端情况下防爆破能力暂时失效,但保证 Redis 故障不打挂登录主链路
 * (单机自用,系统可用优先)。</p>
 *
 * @author inventory
 */
@Component
public class LoginGuard {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginGuard.class);

    /** 失败记录 key 前缀。 */
    private static final String KEY_PREFIX = "login:fail:";

    /** Hash 字段:连续失败次数。 */
    private static final String FIELD_COUNT = "count";

    /** Hash 字段:锁定截止时间(epoch millis,0 = 未锁定)。 */
    private static final String FIELD_LOCKED_UNTIL = "lockedUntil";

    /** key TTL(秒):与默认 lock-seconds 一致,到期 key 消失 = 计数重置。 */
    private static final long KEY_TTL_SECONDS = 300L;

    /** 连续失败次数上限(含)。 */
    @Value("${login.max-attempts:5}")
    private int maxAttempts;

    /** 锁定时长(秒)。 */
    @Value("${login.lock-seconds:300}")
    private long lockSeconds;

    /** 毫秒→秒向上取整的余数补值。 */
    private static final long MILLISECOND_ROUNDUP = 999L;

    /** Redis 字符串模板。 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 构造守卫。
     *
     * @param redisTemplate Redis 字符串模板
     */
    public LoginGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查锁定状态:锁定中直接抛 429(消息含剩余秒数);锁定已到期的记录自动清除。
     *
     * @param username 用户名
     */
    public void checkLocked(String username) {
        Map<Object, Object> hash;
        try {
            hash = redisTemplate.opsForHash().entries(KEY_PREFIX + username);
        } catch (Exception e) {
            // fail-open:Redis 故障放行,保证登录主链路可用
            LOGGER.warn("登录锁定状态读 Redis 失败,放行: {}", e.getMessage());
            return;
        }
        if (hash == null || hash.isEmpty()) {
            return;
        }
        long lockUntilAt = parseLong(hash.get(FIELD_LOCKED_UNTIL));
        long now = System.currentTimeMillis();
        if (lockUntilAt > now) {
            long remainSeconds = (lockUntilAt - now + MILLISECOND_ROUNDUP) / 1000L;
            throw new BizException("登录失败次数过多,请 " + remainSeconds + " 秒后重试",
                    "auth_locked", ErrorCode.HTTP_TOO_MANY_REQUESTS);
        }
        if (lockUntilAt > 0) {
            // 锁定窗口已过:计数整体重置(与内存版一致)
            safeDelete(username);
        }
    }

    /**
     * 记录一次登录失败:未锁定时计数 +1,达到上限进入锁定;锁定中不累加。
     *
     * @param username 用户名
     */
    public void recordFailure(String username) {
        String key = KEY_PREFIX + username;
        try {
            long count = parseLong(
                    redisTemplate.opsForHash().get(key, FIELD_COUNT));
            long lockUntilAt = parseLong(
                    redisTemplate.opsForHash().get(key, FIELD_LOCKED_UNTIL));
            if (lockUntilAt > System.currentTimeMillis()) {
                // 锁定中不累加
                return;
            }
            count++;
            if (count >= maxAttempts) {
                lockUntilAt = System.currentTimeMillis() + lockSeconds * 1000L;
            }
            Map<String, String> values = Map.of(
                    FIELD_COUNT, String.valueOf(count),
                    FIELD_LOCKED_UNTIL, String.valueOf(lockUntilAt));
            redisTemplate.opsForHash().putAll(key, values);
            redisTemplate.expire(key, KEY_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // fail-open:计数失败只告警,不阻断登录主链路
            LOGGER.warn("登录失败计数写 Redis 失败: {}", e.getMessage());
        }
    }

    /**
     * 登录成功:清零该用户名的失败记录。
     *
     * @param username 用户名
     */
    public void reset(String username) {
        safeDelete(username);
    }

    /**
     * 删除失败记录 key(失败只告警,不抛出)。
     *
     * @param username 用户名
     */
    private void safeDelete(String username) {
        try {
            redisTemplate.delete(List.of(KEY_PREFIX + username));
        } catch (Exception e) {
            LOGGER.warn("登录失败记录删 Redis 失败: {}", e.getMessage());
        }
    }

    /**
     * 解析 Hash 字段为 long(缺失/非法返回 0)。
     *
     * @param raw 原始值(可为 null)
     * @return long 值
     */
    private static long parseLong(Object raw) {
        if (raw == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(raw));
    }
}
