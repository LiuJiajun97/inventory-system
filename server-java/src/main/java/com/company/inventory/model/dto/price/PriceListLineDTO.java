package com.company.inventory.model.dto.price;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 价目表行入参(V26:物品不含税单价,税率可空=NULL 按物品默认税率)。
 *
 * @param itemId    物品 ID
 * @param unitPrice 不含税单价
 * @param taxRate   税率(百分数,可空)
 * @author inventory
 */
public record PriceListLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "不含税单价必填")
        @DecimalMin(value = "0", message = "不含税单价不能为负") BigDecimal unitPrice,
        @DecimalMin(value = "0", message = "税率不能为负") BigDecimal taxRate) {
}
