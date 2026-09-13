package com.company.inventory.common.exception;

import com.company.inventory.common.constant.ErrorCode;





import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器:统一契约错误体 {statusCode, code, error, message}。
 *
 * <p>错误体结构与 Fastify 版逐字段对齐:
 * 401/403/404 业务错带 code 字段;参数校验错只带 statusCode/error/message(与快照结构一致);
 * 兜底 500 记录日志。</p>
 *
 * @author inventory
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常:按 BizException 自带的状态码与错误码输出。
     *
     * @param e 业务异常
     * @return 契约错误响应
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getStatus())
                .body(errorBody(e.getStatus(), e.getCode(), e.getMessage()));
    }

    /**
     * 处理请求体参数校验失败(@Valid):400,结构与快照的校验错误一致(不带 code)。
     *
     * @param e 校验异常
     * @return 契约错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        StringBuilder message = new StringBuilder("参数校验失败: ");
        boolean first = true;
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            if (!first) {
                message.append("; ");
            }
            first = false;
            message.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.HTTP_BAD_REQUEST)
                .body(noCodeErrorBody(ErrorCode.HTTP_BAD_REQUEST, message.toString()));
    }

    /**
     * 处理请求体不可读(JSON 格式错误等):400。
     *
     * @param e 不可读异常
     * @return 契约错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.HTTP_BAD_REQUEST)
                .body(noCodeErrorBody(ErrorCode.HTTP_BAD_REQUEST, "请求体格式错误"));
    }

    /**
     * 处理查询参数类型不匹配:400。
     *
     * @param e 类型不匹配异常
     * @return 契约错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(ErrorCode.HTTP_BAD_REQUEST)
                .body(noCodeErrorBody(ErrorCode.HTTP_BAD_REQUEST, "参数 " + e.getName() + " 类型错误"));
    }

    /**
     * 处理路由不存在(Spring 6 静态资源变体):404,消息格式与 Fastify 版一致(路由不存在: METHOD URL)。
     * 注意:一个 @ExceptionHandler 数组里不能把两种异常都声明为方法参数,Spring 只注入实际
     * 抛出的那一个,另一个参数无解析器会抛 IllegalStateException,故拆成两个方法。
     *
     * @param e       异常
     * @param request 当前请求
     * @return 契约错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(
            NoResourceFoundException e, HttpServletRequest request) {
        return notFoundBody(request);
    }

    /**
     * 处理路由不存在(NoHandlerFoundException 变体):404。
     *
     * @param e       异常
     * @param request 当前请求
     * @return 契约错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandler(
            NoHandlerFoundException e, HttpServletRequest request) {
        return notFoundBody(request);
    }

    private ResponseEntity<Map<String, Object>> notFoundBody(HttpServletRequest request) {
        String message = "路由不存在: " + request.getMethod() + " " + request.getRequestURI();
        return ResponseEntity.status(ErrorCode.HTTP_NOT_FOUND)
                .body(errorBody(ErrorCode.HTTP_NOT_FOUND, ErrorCode.NOT_FOUND, message));
    }

    /**
     * 处理方法不被支持:405。
     *
     * @param e 异常
     * @return 契约错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(errorBody(HttpStatus.METHOD_NOT_ALLOWED.value(), ErrorCode.BIZ_ERROR,
                        "请求方法不被支持: " + e.getMethod()));
    }

    /**
     * 处理数据完整性冲突(唯一约束等,如并发重复生成):400,事务已整体回滚,数据安全无损。
     *
     * @param e 数据完整性异常
     * @return 契约错误响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        LOGGER.warn("数据完整性冲突: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.HTTP_BAD_REQUEST)
                .body(noCodeErrorBody(ErrorCode.HTTP_BAD_REQUEST, "操作冲突,请刷新后重试"));
    }

    /**
     * 兜底:未处理异常一律 500,记录日志,不向客户端泄漏堆栈。
     *
     * @param e 未知异常
     * @return 契约错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception e) {
        LOGGER.error("未处理异常", e);
        return ResponseEntity.status(ErrorCode.HTTP_INTERNAL_ERROR)
                .body(noCodeErrorBody(ErrorCode.HTTP_INTERNAL_ERROR, "服务器内部错误"));
    }

    /**
     * 组装带 code 的契约错误体(字段顺序与快照一致)。
     *
     * @param status  HTTP 状态码
     * @param code    错误码
     * @param message 错误信息
     * @return 错误体 Map
     */
    private Map<String, Object> errorBody(int status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statusCode", status);
        body.put("code", code);
        body.put("error", statusText(status));
        body.put("message", message);
        return body;
    }

    /**
     * 组装不带 code 的契约错误体(参数校验/兜底 500,与快照结构一致)。
     *
     * @param status  HTTP 状态码
     * @param message 错误信息
     * @return 错误体 Map
     */
    private Map<String, Object> noCodeErrorBody(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statusCode", status);
        body.put("error", statusText(status));
        body.put("message", message);
        return body;
    }

    /**
     * HTTP 状态码对应的英文状态文本(与 Fastify 版 error 字段一致)。
     *
     * @param status HTTP 状态码
     * @return 状态文本
     */
    private String statusText(int status) {
        return switch (status) {
            case ErrorCode.HTTP_BAD_REQUEST -> "Bad Request";
            case ErrorCode.HTTP_UNAUTHORIZED -> "Unauthorized";
            case ErrorCode.HTTP_FORBIDDEN -> "Forbidden";
            case ErrorCode.HTTP_NOT_FOUND -> "Not Found";
            case ErrorCode.HTTP_METHOD_NOT_ALLOWED -> "Method Not Allowed";
            case ErrorCode.HTTP_INTERNAL_ERROR -> "Internal Server Error";
            default -> "Error";
        };
    }
}
