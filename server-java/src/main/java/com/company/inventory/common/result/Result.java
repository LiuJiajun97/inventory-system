package com.company.inventory.common.result;






/**
 * 统一返回体(阿里规范,仅内部 Service 层之间传递时使用)。
 *
 * <p>注意:Controller 出口必须转成契约快照格式(裸对象/裸数组/{rows,total,page,pageSize}),
 * 不得直接把 Result 序列化给前端。</p>
 *
 * @param code    业务码,0 表示成功
 * @param message 中文提示信息
 * @param data    业务数据
 * @param <T>     数据类型
 * @author inventory
 */
public record Result<T>(int code, String message, T data) {

    /**
     * 构造成功返回。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功 Result
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "成功", data);
    }

    /**
     * 构造失败返回。
     *
     * @param code    业务码
     * @param message 中文提示信息
     * @param <T>     数据类型
     * @return 失败 Result
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
