package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 仓库列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/warehouses 的查询参数绑定。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class WarehouseQuery extends PageQuery {

    /** 关键字(可空,对编码/名称模糊)。 */
    private String keyword;

    /** 仓库类型字典值(可空,如 raw/finished)。 */
    private String warehouseType;


}
