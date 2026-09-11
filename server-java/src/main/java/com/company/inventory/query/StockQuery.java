package com.company.inventory.query;

import com.company.inventory.common.page.PageQuery;

/**
 * 库存列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/stock 的查询参数绑定。</p>
 *
 * @author inventory
 */
public class StockQuery extends PageQuery {

    /** 仓库 ID(可空)。 */
    private Long warehouseId;

    /** 物品编码/名称模糊关键词(可空,不区分大小写)。 */
    private String itemKeyword;

    /** 批次号模糊关键词(可空)。 */
    private String batchNo;

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

    /**
     * 获取物品关键词。
     *
     * @return 物品编码/名称模糊关键词(可空)
     */
    public String getItemKeyword() {
        return itemKeyword;
    }

    /**
     * 设置物品关键词。
     *
     * @param itemKeyword 物品编码/名称模糊关键词(可空)
     */
    public void setItemKeyword(String itemKeyword) {
        this.itemKeyword = itemKeyword;
    }

    /**
     * 获取批次号关键词。
     *
     * @return 批次号模糊关键词(可空)
     */
    public String getBatchNo() {
        return batchNo;
    }

    /**
     * 设置批次号关键词。
     *
     * @param batchNo 批次号模糊关键词(可空)
     */
    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }
}
