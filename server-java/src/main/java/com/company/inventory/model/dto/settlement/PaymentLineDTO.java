package com.company.inventory.model.dto.settlement;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 核销行入参 DTO(挂 confirmed 正票,部分核销)。
 *
 * @param invoiceId 发票 ID
 * @param amount    核销额(正数)
 * @author inventory
 */
public record PaymentLineDTO(@NotNull Long invoiceId, @NotNull BigDecimal amount) {
}
