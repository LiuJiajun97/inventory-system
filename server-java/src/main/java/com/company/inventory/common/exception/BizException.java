package com.company.inventory.common.exception;

import com.company.inventory.common.constant.ErrorCode;





/**
 * 业务异常(阿里规范:业务异常统一用 BizException,禁止裸 RuntimeException 出 Controller)。
 *
 * <p>携带错误码 + 中文 message + HTTP 状态码,由 {@link GlobalExceptionHandler} 统一转成
 * 契约错误体 {statusCode, code, error, message}。</p>
 *
 * @author inventory
 */
public class BizException extends RuntimeException {

    /** 错误码。 */
    private final String code;

    /** HTTP 状态码。 */
    private final int status;

    /**
     * 构造业务异常(默认 400 + BIZ_ERROR)。
     *
     * @param message 中文错误信息
     */
    public BizException(String message) {
        this(message, ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
    }

    /**
     * 构造业务异常。
     *
     * @param message 中文错误信息
     * @param code    错误码
     * @param status  HTTP 状态码
     */
    public BizException(String message, String code, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * 未登录异常(401)。
     *
     * @param message 中文错误信息
     * @return BizException
     */
    public static BizException unauthorized(String message) {
        return new BizException(message, ErrorCode.UNAUTHORIZED, ErrorCode.HTTP_UNAUTHORIZED);
    }

    /**
     * 无权限异常(403)。
     *
     * @param message 中文错误信息
     * @return BizException
     */
    public static BizException forbidden(String message) {
        return new BizException(message, ErrorCode.FORBIDDEN, ErrorCode.HTTP_FORBIDDEN);
    }

    /**
     * 资源不存在异常(404)。
     *
     * @param message 中文错误信息
     * @return BizException
     */
    public static BizException notFound(String message) {
        return new BizException(message, ErrorCode.NOT_FOUND, ErrorCode.HTTP_NOT_FOUND);
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取 HTTP 状态码。
     *
     * @return 状态码
     */
    public int getStatus() {
        return status;
    }
}
