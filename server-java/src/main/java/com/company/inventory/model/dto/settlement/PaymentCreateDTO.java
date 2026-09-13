package com.company.inventory.model.dto.settlement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建付款/收款单入参 DTO(create 即生效,总额=核销行合计,服务端重算)。
 *
 * @param payType 单据类型(payment 付款 | receipt 收款)
 * @param partyId 对方 ID(付款=供应商,收款=客户)
 * @param payDate 付款/收款日期
 * @param remark  备注
 * @param lines   核销行(发票 ID + 核销额)
 * @author inventory
 */
public record PaymentCreateDTO(@NotBlank String payType, @NotNull Long partyId,
        @NotNull LocalDate payDate, String remark,
        @NotEmpty @Valid List<PaymentLineDTO> lines) {
}
