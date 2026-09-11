package com.company.inventory.model.dto.adjust;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 库存调整单行入参(qty 为绝对值,方向由表头 adjustType 决定)。
 *
 * @param itemId     物品 ID
 * @param qty        调整数量(正数,绝对值)
 * @param unitPrice  成本参考价(可空,报损二期入账用)
 * @param batchId    批次 ID(可空,盘点差异生成时携带,选批一致)
 * @param locationId 库位 ID(可空)
 * @param reason     原因
 * @author inventory
 */
public record StockAdjustLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "调整数量必填")
        @Positive(message = "调整数量必须为正数") BigDecimal qty,
        BigDecimal unitPrice,
        @Positive(message = "批次 ID 必须为正数") Long batchId,
        @Positive(message = "库位 ID 必须为正数") Long locationId,
        String reason) {
}
