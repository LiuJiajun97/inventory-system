package com.company.inventory.model.query.report;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存成本(移动均价)报表查询条件。
 *
 * <p>对应 GET /api/v1/reports/cost 的查询参数绑定;
 * warehouseId/itemId 可空(不传即全部),date 为回放截止日(yyyy-MM-dd,含当日,不传默认当前)。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class CostReportQuery {

    /** 仓库 ID(可空)。 */
    private Long warehouseId;

    /** 物品 ID(可空)。 */
    private Long itemId;

    /** 回放截止日(yyyy-MM-dd,可空;回放截止到该日 23:59:59,不传默认当前)。 */
    private String date;
}
