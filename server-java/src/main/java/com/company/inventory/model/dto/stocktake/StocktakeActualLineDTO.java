package com.company.inventory.model.dto.stocktake;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 盘点实盘录入行(actualQty 为 null 表示该行取消实盘)。
 *
 * @param lineId    盘点行 ID
 * @param actualQty 实盘数量(可空=未盘)
 * @author inventory
 */
public record StocktakeActualLineDTO(
        @NotNull(message = "盘点行 ID 必填") Long lineId,
        BigDecimal actualQty) {
}
