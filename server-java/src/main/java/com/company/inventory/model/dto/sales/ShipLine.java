package com.company.inventory.model.dto.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 销售发货回写行(出库单行 → 销售订单行)。
 *
 * @param orderLineId 销售订单行 ID
 * @param qty         实发数量
 * @author inventory
 */
public record ShipLine(
        @NotNull Long orderLineId,
        @NotNull
        @Positive BigDecimal qty) {
}
