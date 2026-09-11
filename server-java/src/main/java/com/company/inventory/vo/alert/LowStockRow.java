package com.company.inventory.vo.alert;

import java.math.BigDecimal;

/**
 * 低库存预警查询行(数据库层结果,内部使用)。
 *
 * @param itemId       物品 ID
 * @param itemCode     物品编码
 * @param itemName     物品名称
 * @param unit         单位
 * @param minStock     最低库存预警线
 * @param totalQty     全仓库存合计
 * @param availableQty 全仓可用合计
 * @author inventory
 */
public record LowStockRow(Long itemId, String itemCode, String itemName, String unit,
        BigDecimal minStock, BigDecimal totalQty, BigDecimal availableQty) {
}
