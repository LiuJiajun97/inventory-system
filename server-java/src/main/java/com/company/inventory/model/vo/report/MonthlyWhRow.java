package com.company.inventory.model.vo.report;

import java.math.BigDecimal;

/**
 * 月报行展开的仓库期末明细 SQL 行(该物品在指定仓库的期末量)。
 *
 * @param itemId      物品 ID
 * @param warehouseId 仓库 ID
 * @param qty         该仓期末量
 * @author inventory
 */
public record MonthlyWhRow(Long itemId, Long warehouseId, BigDecimal qty) {
}
