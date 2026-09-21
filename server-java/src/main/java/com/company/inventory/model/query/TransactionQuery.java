package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存流水列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/transactions 的查询参数绑定;
 * from/to 为字符串(兼容 ISO 本地时间或带 Z 的 UTC 时间),由服务层解析。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class TransactionQuery extends PageQuery {

    /** 仓库 ID(可空)。 */
    private Long warehouseId;

    /** 物品 ID(可空)。 */
    private Long itemId;

    /** 开始时间(可空,含,yyyy-MM-dd 或 ISO 格式字符串)。 */
    private String from;

    /** 结束时间(可空,含,yyyy-MM-dd 或 ISO 格式字符串)。 */
    private String to;

    /** 业务编码(可空,inbound/outbound)。 */
    private String bizCode;

}
