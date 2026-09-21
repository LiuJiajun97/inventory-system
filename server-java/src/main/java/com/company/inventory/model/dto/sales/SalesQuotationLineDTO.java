package com.company.inventory.model.dto.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 销售报价单行入参(V25,六列金额照抄销售订单行,服务端价税重算)。
 *
 * @param itemId     物品 ID
 * @param quantity   数量(正数)
 * @param unitPrice  不含税单价(与 taxPrice 二选一)
 * @param taxPrice   含税单价(与 unitPrice 二选一)
 * @param taxRate    税率(百分数,可空默认 13)
 * @param remark     行备注
 * @author inventory
 */
public record SalesQuotationLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "数量必填")
        @Positive(message = "数量必须为正数") BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxPrice,
        BigDecimal taxRate,
        String remark) {
}