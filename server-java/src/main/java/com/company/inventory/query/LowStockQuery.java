package com.company.inventory.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 低库存预警列表查询条件。
 *
 * @author inventory
 */
public class LowStockQuery extends PageQuery {

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

    /** 物品编码或名称关键字。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String itemKeyword;

    /**
     * 获取物品编码或名称关键字。
     *
     * @return 物品编码或名称关键字
     */
    public String getItemKeyword() {
        return itemKeyword;
    }

    /**
     * 设置物品编码或名称关键字。
     *
     * @param itemKeyword 物品编码或名称关键字
     */
    public void setItemKeyword(String itemKeyword) {
        this.itemKeyword = itemKeyword;
    }
}
