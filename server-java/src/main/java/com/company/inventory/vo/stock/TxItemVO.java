package com.company.inventory.vo.stock;
/**
 * 流水行内嵌的物品精简对象(契约:仅 id + itemCode + itemName)。
 *
 * @param id       主键
 * @param itemCode 物品编码
 * @param itemName 物品名称
 * @author inventory
 */
public record TxItemVO(Long id, String itemCode, String itemName) {
}
