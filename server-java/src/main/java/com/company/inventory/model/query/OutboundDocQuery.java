package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 出库单列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/outbound 的查询参数绑定。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class OutboundDocQuery extends PageQuery {

    /** 仓库 ID(可空,指定时仅查该仓库单据)。 */
    private Long warehouseId;


}
