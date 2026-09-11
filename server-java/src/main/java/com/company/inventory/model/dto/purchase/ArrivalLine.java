package com.company.inventory.model.dto.purchase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 采购到货回写行(入库单行 → 采购订单行)。
 *
 * @param orderLineId 采购订单行 ID
 * @param qty         实收数量
 * @author inventory
 */
public record ArrivalLine(
        @NotNull Long orderLineId,
        @NotNull
        @Positive BigDecimal qty) {
}
