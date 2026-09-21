package com.company.inventory.model.dto.purchase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请购单行入参(V25,无金额合计,行价可空不汇总)。
 *
 * @param itemId       物品 ID
 * @param quantity     数量(正数)
 * @param expectedDate 期望到货日(可空)
 * @param unitPrice    参考单价(可空,仅参考)
 * @param remark       行备注
 * @author inventory
 */
public record PurchaseRequisitionLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "数量必填")
        @Positive(message = "数量必须为正数") BigDecimal quantity,
        LocalDate expectedDate,
        BigDecimal unitPrice,
        String remark) {
}