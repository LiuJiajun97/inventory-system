package com.company.inventory.model.dto.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售订单行入参(V20:不含税单价与含税单价二选一,服务端校验并统一重算)。
 *
 * @param itemId               物品 ID
 * @param orderedQty           订购数量(正数)
 * @param customerDeliveryDate 客户要货日(可空)
 * @param unitPrice            不含税单价(与 taxPrice 二选一必填)
 * @param taxPrice             含税单价(与 unitPrice 二选一必填)
 * @param taxRate              税率(百分数,可空默认 13)
 * @param lineRemark           行备注
 * @author inventory
 */
public record SalesOrderLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "订购数量必填")
        @Positive(message = "订购数量必须为正数") BigDecimal orderedQty,
        LocalDate customerDeliveryDate,
        BigDecimal unitPrice,
        BigDecimal taxPrice,
        BigDecimal taxRate,
        String lineRemark) {
}
