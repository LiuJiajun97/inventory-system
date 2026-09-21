package com.company.inventory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 请求链路追踪过滤器:为每个请求生成/透传 traceId,写入 MDC 并回响应头。
 *
 * <p>规则:请求头带 W3C traceparent 且 trace-id 段合法(32 位小写 hex、非全 0)
 * → 透传该 trace-id;否则本地生成 16 位 hex。traceId 经 MDC(traceId)注入日志
 * (JSON 结构化日志自动携带),并回响应头 X-Trace-Id;覆盖所有请求(含 404/异常),
 * finally 中清除 MDC 防线程池串号。</p>
 *
 * @author inventory
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 键:请求追踪 ID。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** 响应头:回传本次请求的 traceId。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** W3C 追踪上下文请求头。 */
    private static final String HEADER_TRACE_PARENT = "traceparent";

    /** 本地生成的 traceId 长度(16 位 hex)。 */
    private static final int LOCAL_TRACE_ID_LENGTH = 16;

    /** W3C trace-id 段长度(32 位 hex)。 */
    private static final int W3C_TRACE_ID_LENGTH = 32;

    /** 随机数生成器(本地 traceId)。 */
    private final SecureRandom random = new SecureRandom();

    /**
     * 过滤:解析/生成 traceId → MDC + 响应头 → 放行 → 清理 MDC。
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
        String traceId = resolveTraceId(request);
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /**
     * 解析 traceId:优先透传 W3C traceparent 的 trace-id 段,否则本地生成。
     *
     * @param request 请求
     * @return traceId
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceParent = request.getHeader(HEADER_TRACE_PARENT);
        if (traceParent != null && !traceParent.isBlank()) {
            String[] parts = traceParent.trim().split("-");
            if (parts.length >= 2) {
                String traceIdSegment = parts[1];
                if (isW3cTraceId(traceIdSegment)) {
                    return traceIdSegment;
                }
            }
        }
        return newLocalTraceId();
    }

    /**
     * 校验 W3C trace-id 段:32 位 hex 且非全 0(全 0 为 W3C 保留值)。
     *
     * @param segment trace-id 段
     * @return true = 合法
     */
    private boolean isW3cTraceId(String segment) {
        if (segment.length() != W3C_TRACE_ID_LENGTH) {
            return false;
        }
        if (segment.chars().allMatch(c -> c == '0')) {
            return false;
        }
        for (char c : segment.toCharArray()) {
            char lower = Character.toLowerCase(c);
            if ((lower < '0' || lower > '9') && (lower < 'a' || lower > 'f')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 本地生成 16 位 hex traceId。
     *
     * @return 新生成的 traceId
     */
    private String newLocalTraceId() {
        byte[] bytes = new byte[LOCAL_TRACE_ID_LENGTH / 2];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
