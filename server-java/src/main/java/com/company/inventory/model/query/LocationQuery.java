package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 库位列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/locations 的查询参数绑定。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class LocationQuery extends PageQuery {

    /** 仓库 ID(可空,指定时仅查该仓库库位)。 */
    private Long warehouseId;


}
