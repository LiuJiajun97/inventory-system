package com.company.inventory.config;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.support.IdempotencyResult;
import com.company.inventory.common.support.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 写接口幂等拦截器(标准 HTTP Idempotency-Key 头,at-least-once 重试去重)。
 *
 * <p>仅处理 /api/v1/** 的 POST/PUT/DELETE:
 * 无 key 直接放行(向后兼容);有 key 命中成功响应缓存 → 原样重放首次响应;
 * 命中"进行中"占位(并发重复)→ 429 idempotent_conflict;
 * 未命中 → 抢占位,完成后把成功响应体写入缓存(TTL 5 分钟)。</p>
 *
 * <p>响应体读取依赖伴随的 {@link IdempotencyKeyFilter} 对响应做
 * ContentCachingResponseWrapper 包裹(拦截器无法替换 servlet 链上的响应对象),
 * 本拦截器在 afterCompletion 读取缓存的响应体并落缓存/释放占位。</p>
 *
 * @author inventory
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyInterceptor.class);

    /** 幂等键请求头名(HTTP 标准)。 */
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    /** 请求属性:本次请求成功抢占到占位的幂等键。 */
    public static final String ATTR_IDEMPOTENCY_KEY = "idempotency.key";

    /** 重复请求并发冲突的英文错误标识(与错误体 error 字段一致)。 */
    public static final String ERROR_IDEMPOTENT_CONFLICT = "idempotent_conflict";

    /** 并发重复消息。 */
    private static final String MSG_CONFLICT = "重复请求,请稍后重试";

    /** HTTP 2xx 成功状态下限。 */
    private static final int STATUS_OK_LOWER_BOUND = 200;

    /** HTTP 3xx 状态上限(重定向亦视为首次未达成功终点,不落结果缓存)。 */
    private static final int STATUS_REDIRECT_UPPER_BOUND = 300;

    /** 拦截器依赖的幂等支撑。 */
    private final IdempotencySupport idempotencySupport;

    /** JSON 序列化器(写 429 错误体)。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造拦截器。
     *
     * @param idempotencySupport 幂等支撑
     * @param objectMapper       JSON 序列化器
     */
    public IdempotencyInterceptor(IdempotencySupport idempotencySupport, ObjectMapper objectMapper) {
        this.idempotencySupport = idempotencySupport;
        this.objectMapper = objectMapper;
    }

    /**
     * 前置处理:按 Idempotency-Key 命中缓存重放 / 拦截并发重复 / 抢占占位。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return true 放行;false 已写出响应(重放或 429)
     * @throws java.io.IOException IO 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws java.io.IOException {
        if (!isWritable(request)) {
            return true;
        }
        String key = request.getHeader(HEADER_IDEMPOTENCY_KEY);
        if (key == null || key.isBlank()) {
            // 无 key:不处理,向后兼容
            return true;
        }
        IdempotencyResult cached = idempotencySupport.getResult(key);
        if (cached != null) {
            // 命中:重放首次成功响应
            replay(response, cached);
            return false;
        }
        if (!idempotencySupport.putIfAbsent(key)) {
            // 占位已被并发请求抢走且尚无结果:并发重复
            writeConflict(response);
            return false;
        }
        request.setAttribute(ATTR_IDEMPOTENCY_KEY, key);
        return true;
    }

    /**
     * 后置处理:把成功响应体写入缓存;失败响应释放占位(允许客户端同 key 重试)。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @param ex       异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        String key = (String) request.getAttribute(ATTR_IDEMPOTENCY_KEY);
        Object wrappedAttr = request.getAttribute(IdempotencyKeyFilter.ATTR_CACHED_RESPONSE);
        if (key == null || !(wrappedAttr instanceof ContentCachingResponseWrapper wrapper)) {
            return;
        }
        byte[] body = wrapper.getContentAsByteArray();
        int status = wrapper.getStatus();
        if (status >= STATUS_OK_LOWER_BOUND && status < STATUS_REDIRECT_UPPER_BOUND) {
            idempotencySupport.putResult(key, status, wrapper.getContentType(), body);
        } else {
            // 首次请求未成功:释放占位,客户端同 key 重试不再被 429 拦
            idempotencySupport.evict(key);
            LOGGER.debug("幂等 key 首次请求失败(状态 {}),释放占位: {}", status, key);
        }
    }

    /**
     * 是否是需要幂等处理的写请求(/api/v1/** 的 POST/PUT/DELETE)。
     *
     * @param request 请求
     * @return true = 写请求
     */
    private boolean isWritable(HttpServletRequest request) {
        String method = request.getMethod();
        boolean writable = HttpMethod.POST.name().equals(method)
                || HttpMethod.PUT.name().equals(method)
                || HttpMethod.DELETE.name().equals(method);
        return writable && request.getRequestURI().startsWith("/api/v1/");
    }

    /**
     * 重放缓存的首次成功响应(状态码、Content-Type、body 原样)。
     *
     * @param response 响应
     * @param cached   缓存结果
     * @throws java.io.IOException IO 异常
     */
    private void replay(HttpServletResponse response, IdempotencyResult cached)
            throws java.io.IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        }
        response.getOutputStream().write(cached.body());
    }

    /**
     * 输出 429 并发重复错误体。
     *
     * @param response 响应
     * @throws java.io.IOException IO 异常
     */
    private void writeConflict(HttpServletResponse response) throws java.io.IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statusCode", ErrorCode.HTTP_TOO_MANY_REQUESTS);
        body.put("error", ERROR_IDEMPOTENT_CONFLICT);
        body.put("message", MSG_CONFLICT);
        response.setStatus(ErrorCode.HTTP_TOO_MANY_REQUESTS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
