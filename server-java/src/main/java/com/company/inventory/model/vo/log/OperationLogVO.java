package com.company.inventory.model.vo.log;

import java.time.LocalDateTime;

/**
 * 操作日志行视图(契约:GET /api/v1/operation-logs 列表行)。
 *
 * @param id         主键
 * @param username   操作用户名
 * @param ip         请求 IP(可空)
 * @param module     模块中文名
 * @param action     HTTP 方法(POST/PUT/DELETE)
 * @param path       请求路径
 * @param targetType 操作对象类型中文名(可空)
 * @param targetId   操作对象 ID(可空)
 * @param success    结果(1 成功 0 失败)
 * @param errorMsg   失败时的异常消息(可空)
 * @param costMs     接口耗时毫秒(可空)
 * @param createdAt  创建时间
 * @author inventory
 */
public record OperationLogVO(
        long id,
        String username,
        String ip,
        String module,
        String action,
        String path,
        String targetType,
        Long targetId,
        Integer success,
        String errorMsg,
        Integer costMs,
        LocalDateTime createdAt) {
}
