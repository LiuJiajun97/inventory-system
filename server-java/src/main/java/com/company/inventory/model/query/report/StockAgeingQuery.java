package com.company.inventory.model.query.report;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 库龄/呆滞报表查询条件。
 *
 * <p>对应 GET /api/v1/reports/stock-ageing 的查询参数绑定;
 * ageFrom/ageTo 为库龄天数区间(可空,stagnantDays 默认 90)。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class StockAgeingQuery extends PageQuery {

    /** 呆滞判定默认天数(无出方向流水超过该天数即呆滞)。 */
    public static final int DEFAULT_STAGNANT_DAYS = 90;

    /** 仓库 ID(可空)。 */
    private Long warehouseId;

    /** 物品 ID(可空,精确筛选;与 itemKeyword 二选一,ID 优先)。 */
    private Long itemId;

    /** 物品关键字(编码或名称模糊,可空)。 */
    private String itemKeyword;

    /** 库龄天数区间起(可空,含)。 */
    private Integer ageFrom;

    /** 库龄天数区间止(可空,含)。 */
    private Integer ageTo;

    /** 呆滞判定天数(可空,默认 90)。 */
    private Integer stagnantDays;
}
