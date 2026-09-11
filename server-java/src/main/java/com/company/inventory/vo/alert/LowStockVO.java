package com.company.inventory.vo.alert;

import java.math.BigDecimal;

/**
 * 低库存预警行出参(物品 minStock 非空且全仓可用量 < minStock)。
 *
 * @param itemId       物品 ID
 * @param itemCode     物品编码
 * @param itemName     物品名称
 * @param unit         单位
 * @param minStock     最低库存预警线
 * @param totalQty     全仓库存合计(字符串)
 * @param availableQty 全仓可用合计(字符串)
 * @author inventory
 */
public record LowStockVO(Long itemId, String itemCode, String itemName, String unit,
        BigDecimal minStock, String totalQty, String availableQty) {
}
