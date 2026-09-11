package com.company.inventory.config;







import com.company.inventory.common.support.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 拦截器:校验 Authorization: Bearer &lt;token&gt;,注入用户信息,并按 {@link RequireRole} 做角色鉴权。
 *
 * <p>无 token / token 无效 / 过期 → 401 未登录;角色不符 → 403 无权限。
 * 错误体结构与 Fastify 版逐字段一致。</p>
 *
 * @author inventory
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtInterceptor.class);

    /** 请求属性:用户 ID。 */
    public static final String ATTR_USER_ID = "auth.userId";

    /** 请求属性:用户名。 */
    public static final String ATTR_USERNAME = "auth.username";

    /** 请求属性:角色。 */
    public static final String ATTR_ROLE = "auth.role";

    /** 请求属性:姓名。 */
    public static final String ATTR_NAME = "auth.name";

    /** Authorization 头名称。 */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** HTTP 状态:401。 */
    private static final int HTTP_UNAUTHORIZED = 401;

    /** HTTP 状态:403。 */
    private static final int HTTP_FORBIDDEN = 403;

    /** 未登录消息。 */
    private static final String MSG_UNAUTHORIZED = "未登录";

    /** 无权限消息。 */
    private static final String MSG_FORBIDDEN = "无权限";

    private final JwtParser parser;
    private final SecretKey key;
    private final ObjectMapper objectMapper;
    private final long expiresSeconds;

    /**
     * 构造拦截器。
     *
     * @param jwtProperties  JWT 配置
     * @param objectMapper   JSON 序列化器
     */
    public JwtInterceptor(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.objectMapper = objectMapper;
        this.expiresSeconds = jwtProperties.getExpiresSeconds();
    }

    /**
     * 签发 JWT(payload:sub/username/role/name,过期时间取配置,默认 8 小时)。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色
     * @param name     姓名
     * @return JWT 字符串
     */
    public String issueToken(long userId, String username, String role, String name) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("name", name)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    /**
     * 前置处理:校验 token 与角色。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return true 放行
     * @throws java.io.IOException IO 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws java.io.IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            // 静态资源(swagger 等)不鉴权
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            writeError(response, HTTP_UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", MSG_UNAUTHORIZED);
            return false;
        }

        Claims claims;
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            claims = jws.getPayload();
        } catch (Exception e) {
            // token 无效或过期:按未登录处理,与 Fastify 版一致
            LOGGER.debug("token 校验失败: {}", e.getMessage());
            writeError(response, HTTP_UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", MSG_UNAUTHORIZED);
            return false;
        }

        request.setAttribute(ATTR_USER_ID, Long.parseLong(claims.getSubject()));
        request.setAttribute(ATTR_USERNAME, claims.get("username", String.class));
        request.setAttribute(ATTR_ROLE, claims.get("role", String.class));
        request.setAttribute(ATTR_NAME, claims.get("name", String.class));

        // 写入用户上下文,供 MyBatis-Plus 自动填充读取
        UserContext.set(claims.get("username", String.class));

        // 角色校验:方法注解优先,其次类注解
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            String role = (String) request.getAttribute(ATTR_ROLE);
            if (!Arrays.asList(requireRole.value()).contains(role)) {
                writeError(response, HTTP_FORBIDDEN, "FORBIDDEN", "Forbidden", MSG_FORBIDDEN);
                return false;
            }
        }
        return true;
    }

    /**
     * 后置处理:清除用户上下文,防线程池串号。
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @param ex       异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 从请求头解析 Bearer token。
     *
     * @param request 请求
     * @return token 或 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 输出契约错误体 JSON。
     *
     * @param response 响应
     * @param status   HTTP 状态码
     * @param code     错误码
     * @param error    英文状态文本
     * @param message  中文消息
     * @throws java.io.IOException IO 异常
     */
    private void writeError(HttpServletResponse response, int status, String code,
            String error, String message) throws java.io.IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statusCode", status);
        body.put("code", code);
        body.put("error", error);
        body.put("message", message);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
