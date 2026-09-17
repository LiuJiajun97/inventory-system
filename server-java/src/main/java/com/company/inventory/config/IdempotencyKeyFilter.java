package com.company.inventory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 幂等响应包裹过滤器:为带 Idempotency-Key 的写请求把响应换成
 * {@link ContentCachingResponseWrapper},使 {@link IdempotencyInterceptor}
 * 能在 afterCompletion 读到完整响应体并落幂等缓存。
 *
 * <p>HandlerInterceptor 无法替换 servlet 链上已传递的响应对象,故包裹动作
 * 必须由 Filter 完成(与任务书"包一层 HttpServletResponse"等价落地)。</p>
 *
 * @author inventory
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyKeyFilter.class);

    /** 请求属性:被包裹的响应(未包裹时不存在)。 */
    public static final String ATTR_CACHED_RESPONSE = "idempotency.cachedResponse";

    /** API 前缀。 */
    private static final String API_PREFIX = "/api/v1/";

    /**
     * 过滤:命中包裹条件的请求换响应对象,其余原样放行。
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @throws ServletException servlet 异常
     * @throws IOException        IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!needsWrapping(request)) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        request.setAttribute(ATTR_CACHED_RESPONSE, wrapper);
        try {
            chain.doFilter(request, wrapper);
        } finally {
            // 关键:把缓存的响应体回写给真实输出流,客户端才能收到 body
            try {
                wrapper.copyBodyToResponse();
            } catch (IOException e) {
                LOGGER.warn("幂等响应体回写失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 是否需要包裹:/api/v1/** 的 POST/PUT/DELETE 且带非空 Idempotency-Key 头。
     *
     * @param request 请求
     * @return true = 需要包裹
     */
    private boolean needsWrapping(HttpServletRequest request) {
        String method = request.getMethod();
        boolean writable = HttpMethod.POST.name().equals(method)
                || HttpMethod.PUT.name().equals(method)
                || HttpMethod.DELETE.name().equals(method);
        String key = request.getHeader(IdempotencyInterceptor.HEADER_IDEMPOTENCY_KEY);
        return writable && request.getRequestURI().startsWith(API_PREFIX)
                && key != null && !key.isBlank();
    }
}
