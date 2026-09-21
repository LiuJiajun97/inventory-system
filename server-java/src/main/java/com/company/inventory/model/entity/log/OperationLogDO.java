package com.company.inventory.model.entity.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志表实体(表 operation_log,V16)。
 *
 * <p>主键为 BIGSERIAL:纯日志表量大,自增无并发热点顾虑,与业务表 INTEGER 主键体系无关。</p>
 *
 * @author inventory
 */
@TableName("operation_log")
@Getter
@Setter
public class OperationLogDO {

    /** 主键(BIGSERIAL 自增)。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 操作用户名。 */
    private String username;
    /** 请求 IP(X-Forwarded-For 首段或 remoteAddr,可空)。 */
    private String ip;
    /** 模块中文名(由 Controller 类名映射)。 */
    private String module;
    /** HTTP 方法:POST/PUT/DELETE。 */
    private String action;
    /** 请求路径(如 /api/v1/items)。 */
    private String path;
    /** 操作对象类型中文名(可空)。 */
    private String targetType;
    /** 操作对象 ID(从路径变量取,可空)。 */
    private Long targetId;
    /** 操作对象单号/编码(切面拿不到则不落,可空)。 */
    private String targetNo;
    /** 结果:1 成功 0 失败。 */
    private Integer success;
    /** 失败时的异常消息(截断 500 字符,可空)。 */
    private String errorMsg;
    /** 接口耗时毫秒(可空)。 */
    private Integer costMs;
    /** 创建时间。 */
    private LocalDateTime createdAt;

}
