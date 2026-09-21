package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/stock 的查询参数绑定。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class StockQuery extends PageQuery {

    /** 仓库 ID(可空)。 */
    private Long warehouseId;

    /** 物品编码/名称模糊关键词(可空,不区分大小写)。 */
    private String itemKeyword;

    /** 批次号模糊关键词(可空)。 */
    private String batchNo;

}
