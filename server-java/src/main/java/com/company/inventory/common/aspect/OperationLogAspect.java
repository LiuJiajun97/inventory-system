package com.company.inventory.common.aspect;

import com.company.inventory.common.constant.LogModule;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.UserContext;
import com.company.inventory.mapper.log.OperationLogMapper;
import com.company.inventory.model.entity.log.OperationLogDO;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.LocalDateTime;

/**
 * 操作日志切面(V16):记录全部写操作(POST/PUT/DELETE)的执行流水。
 *
 * <p>切点为 Controller 包下带 @PostMapping/@PutMapping/@DeleteMapping 的方法(GET 不记);
 * 登录/登出排除(登录失败风暴会灌爆日志)。成功/失败都记:业务异常记其消息,
 * 其他异常记"系统异常"。</p>
 *
 * <p>两条铁律:</p>
 * <ul>
 * <li>落库失败绝不抛出——insert 包 try-catch 只 log.warn,日志功能故障不影响业务主流程;</li>
 * <li>零业务查库——对象 ID 只从路径变量取,对象单号/编码切面拿不到就不落,严禁反查表。</li>
 * </ul>
 *
 * @author inventory
 */
@Aspect
@Component
public class OperationLogAspect {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 排除路径:登录(失败风暴灌爆日志)。 */
    private static final String EXCLUDE_LOGIN = "/api/v1/auth/login";
    /** 排除路径:登出。 */
    private static final String EXCLUDE_LOGOUT = "/api/v1/auth/logout";
    /** 异常消息落库长度上限(与表列宽一致)。 */
    private static final int ERROR_MSG_MAX = 500;
    /** 取不到用户名时的兜底值(表列非空)。 */
    private static final String ANONYMOUS = "anonymous";
    /** 纳秒/毫秒换算常量。 */
    private static final long NANOS_PER_MILLIS = 1_000_000L;

    /** 操作日志 Mapper(切面只用 insert,查库逻辑一律不放)。 */
    private final OperationLogMapper operationLogMapper;

    /**
     * 构造切面。
     *
     * @param operationLogMapper 操作日志 Mapper
     */
    public OperationLogAspect(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 写操作切点:Controller 包下带 POST/PUT/DELETE 映射注解的方法(GET 不记)。
     */
    @Pointcut("execution(* com.company.inventory.controller..*.*(..))"
            + " && (@annotation(org.springframework.web.bind.annotation.PostMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public void writeOperations() {
        // 切点声明,无方法体
    }

    /**
     * 环绕通知:计时执行目标方法,成功/失败均落一条操作日志,原异常原样重抛。
     *
     * @param p 连接点
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的原异常(日志记录不影响异常传播)
     */
    @Around("writeOperations()")
    public Object around(ProceedingJoinPoint p) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // 非 HTTP 调用(如单元测试直调 Service 层),不记
            return p.proceed();
        }
        HttpServletRequest request = attrs.getRequest();
        String uri = request.getRequestURI();
        if (EXCLUDE_LOGIN.equals(uri) || EXCLUDE_LOGOUT.equals(uri)) {
            // 登录/登出排除:登录失败风暴会灌爆日志
            return p.proceed();
        }
        long startNanos = System.nanoTime();
        OperationLogDO log = baseLog(request, attrs, p);
        try {
            Object result = p.proceed();
            log.setSuccess(1);
            return result;
        } catch (Throwable e) {
            log.setSuccess(0);
            if (e instanceof BizException biz) {
                log.setErrorMsg(truncate(biz.getMessage()));
            } else {
                LOGGER.warn("写操作系统异常: {} {}", log.getAction(), uri, e);
                log.setErrorMsg("系统异常");
            }
            throw e;
        } finally {
            log.setCostMs((int) ((System.nanoTime() - startNanos) / NANOS_PER_MILLIS));
            saveQuietly(log);
        }
    }

    /**
     * 组装日志基础字段(用户/IP/模块/方法/路径/路径变量对象 ID),零业务查库。
     *
     * @param request HTTP 请求
     * @param attrs   请求属性(取 HandlerMethod)
     * @param p       连接点(HandlerMethod 拿不到时的兜底)
     * @return 待补充结果的日志实体
     */
    private OperationLogDO baseLog(HttpServletRequest request, ServletRequestAttributes attrs,
            ProceedingJoinPoint p) {
        OperationLogDO log = new OperationLogDO();
        String username = UserContext.get();
        log.setUsername(username == null || username.isBlank() ? ANONYMOUS : username);
        log.setIp(clientIp(request));
        log.setPath(request.getRequestURI());
        HandlerMethod handlerMethod = resolveHandlerMethod(attrs);
        if (handlerMethod != null) {
            log.setModule(LogModule.of(handlerMethod.getBeanType()));
            log.setAction(actionOf(handlerMethod));
            fillTargetId(request, handlerMethod, log);
        } else {
            // 兜底(理论上不会发生:Spring MVC 请求必有 BEST_MATCHING_HANDLER)
            log.setModule(LogModule.of(p.getSignature().getDeclaringType()));
            log.setAction("UNKNOWN");
        }
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    /**
     * 从请求属性解析 HandlerMethod(拿不到返回 null,主体字段用兜底)。
     *
     * @param attrs 请求属性
     * @return HandlerMethod 或 null
     */
    private HandlerMethod resolveHandlerMethod(ServletRequestAttributes attrs) {
        Object handler = attrs.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return handler instanceof HandlerMethod handlerMethod ? handlerMethod : null;
    }

    /**
     * 从路径变量提取对象 ID(数字才落,非数字变量如单据号不落);对象类型落模块中文名,零查库。
     *
     * @param request       HTTP 请求
     * @param handlerMethod 处理器方法
     * @param log           日志实体(就地填充)
     */
    private void fillTargetId(HttpServletRequest request, HandlerMethod handlerMethod,
            OperationLogDO log) {
        try {
            String template = requestTemplate(handlerMethod);
            if (!StringUtils.hasText(template)) {
                return;
            }
            PathPattern pattern = PathPatternParser.defaultInstance.parse(template);
            PathPattern.PathMatchInfo matchInfo = pattern.matchAndExtract(
                    PathContainer.parsePath(request.getRequestURI()));
            if (matchInfo == null || matchInfo.getUriVariables().isEmpty()) {
                return;
            }
            for (String value : matchInfo.getUriVariables().values()) {
                Long id = parseId(value);
                if (id != null) {
                    log.setTargetId(id);
                    log.setTargetType(log.getModule());
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析路径变量失败(不影响日志主体): {}", e.getMessage());
        }
    }

    /**
     * 拼处理器方法的完整路径模板(类级 @RequestMapping + 方法级 @RequestMapping)。
     *
     * @param handlerMethod 处理器方法
     * @return 完整路径模板(如 /api/v1/items/{id}),拼不出返回 null
     */
    private String requestTemplate(HandlerMethod handlerMethod) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), RequestMapping.class);
        RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequestMapping.class);
        if (methodMapping == null || methodMapping.value().length == 0) {
            return null;
        }
        String classPath = (classMapping != null && classMapping.value().length > 0)
                ? classMapping.value()[0] : "";
        return classPath + methodMapping.value()[0];
    }

    /**
     * 取 HTTP 方法名(按命中的映射注解,切点已保证三者之一)。
     *
     * @param handlerMethod 处理器方法
     * @return POST/PUT/DELETE
     */
    private String actionOf(HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(PostMapping.class)) {
            return "POST";
        }
        if (handlerMethod.hasMethodAnnotation(PutMapping.class)) {
            return "PUT";
        }
        return "DELETE";
    }

    /**
     * 客户端 IP:X-Forwarded-For 首段,取不到用 remoteAddr。
     *
     * @param request HTTP 请求
     * @return IP 地址
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 路径变量值转 ID(数字才落)。
     *
     * @param value 变量值(可空)
     * @return 数字 ID 或 null
     */
    private Long parseId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 截断异常消息到落库长度上限。
     *
     * @param msg 原始消息(可空)
     * @return 截断后的消息
     */
    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= ERROR_MSG_MAX ? msg : msg.substring(0, ERROR_MSG_MAX);
    }

    /**
     * 落库:失败绝不抛出,只 log.warn(日志功能故障不影响业务主流程)。
     *
     * @param log 日志实体
     */
    private void saveQuietly(OperationLogDO log) {
        try {
            operationLogMapper.insert(log);
        } catch (Exception e) {
            LOGGER.warn("操作日志落库失败(不影响业务): {} {} {}", log.getAction(), log.getPath(),
                    e.getMessage());
        }
    }

}
