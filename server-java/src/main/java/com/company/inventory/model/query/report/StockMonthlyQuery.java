package com.company.inventory.model.query.report;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 进销存月报查询条件(item 维度,跨仓汇总)。
 *
 * <p>对应 GET /api/v1/reports/stock-monthly 的查询参数绑定;
 * from/to 为日期字符串(yyyy-MM-dd,空格分隔东八区约定),由服务层解析。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class StockMonthlyQuery extends PageQuery {

    /** 仓库 ID(可空,空为全仓汇总)。 */
    private Long warehouseId;

    /** 物品 ID(可空,精确筛选;与 itemKeyword 二选一,ID 优先)。 */
    private Long itemId;

    /** 物品关键字(编码或名称模糊,可空)。 */
    private String itemKeyword;

    /** 区间起(可空,含,yyyy-MM-dd)。 */
    private String from;

    /** 区间止(可空,含,yyyy-MM-dd)。 */
    private String to;
}
