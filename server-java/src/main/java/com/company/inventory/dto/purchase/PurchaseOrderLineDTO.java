package com.company.inventory.dto.purchase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单行入参。
 *
 * @param itemId               物品 ID
 * @param orderedQty           订购数量(正数)
 * @param expectedDeliveryDate 计划交货日(可空)
 * @param unitPrice            不含税单价(必填)
 * @param taxRate              税率(百分数,可空默认 13)
 * @param lineRemark           行备注
 * @author inventory
 */
public record PurchaseOrderLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "订购数量必填")
        @Positive(message = "订购数量必须为正数") BigDecimal orderedQty,
        LocalDate expectedDeliveryDate,
        @NotNull(message = "不含税单价必填") BigDecimal unitPrice,
        BigDecimal taxRate,
        String lineRemark) {
}
