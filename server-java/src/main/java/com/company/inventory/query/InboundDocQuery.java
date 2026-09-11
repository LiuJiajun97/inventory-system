package com.company.inventory.query;

import com.company.inventory.common.page.PageQuery;

/**
 * 入库单列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/inbound 的查询参数绑定。</p>
 *
 * @author inventory
 */
public class InboundDocQuery extends PageQuery {

    /** 仓库 ID(可空,指定时仅查该仓库单据)。 */
    private Long warehouseId;

    /**
     * 获取仓库 ID。
     *
     * @return 仓库 ID(可空)
     */
    public Long getWarehouseId() {
        return warehouseId;
    }

    /**
     * 设置仓库 ID。
     *
     * @param warehouseId 仓库 ID(可空)
     */
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }
}
