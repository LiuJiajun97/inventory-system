package com.company.inventory.model.query.log;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/operation-logs 的查询参数绑定;
 * from/to 为字符串(兼容东八区空格分隔或 ISO 格式),由服务层经 DateRangeSupport 解析。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class OperationLogQuery extends PageQuery {

    /** 操作用户名(可空,模糊匹配)。 */
    private String username;

    /** 模块中文名(可空,精确匹配)。 */
    private String module;

    /** 结果(可空,1 成功 / 0 失败)。 */
    private Integer success;

    /** 开始时间(可空,含,yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 字符串)。 */
    private String from;

    /** 结束时间(可空,含,yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss 字符串)。 */
    private String to;

}
