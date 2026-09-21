package com.company.inventory.model.vo.stock;
/**
 * 流水行内嵌的仓库精简对象(契约:仅 id + warehouseName)。
 *
 * @param id            主键
 * @param warehouseName 仓库名称
 * @author inventory
 */
public record TxWarehouseVO(Long id, String warehouseName) {
}
