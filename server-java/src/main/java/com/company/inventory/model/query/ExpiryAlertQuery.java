package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;

/**
 * 临期预警列表查询条件。
 *
 * @author inventory
 */
public class ExpiryAlertQuery extends PageQuery {

    /** 仓库 ID。 */
    @Positive(message = "仓库 ID必须为正数")
    private Long warehouseId;

    /**
     * 获取仓库 ID。
     *
     * @return 仓库 ID
     */
    public Long getWarehouseId() {
        return warehouseId;
    }

    /**
     * 设置仓库 ID。
     *
     * @param warehouseId 仓库 ID
     */
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    /** 物品 ID。 */
    @Positive(message = "物品 ID必须为正数")
    private Long itemId;

    /**
     * 获取物品 ID。
     *
     * @return 物品 ID
     */
    public Long getItemId() {
        return itemId;
    }

    /**
     * 设置物品 ID。
     *
     * @param itemId 物品 ID
     */
    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }
}
