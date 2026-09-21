package com.company.inventory.model.dto.settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 发票行入参 DTO(行级匹配:只传源单据行引用 + 开票额,源行含税额服务端取值)。
 *
 * @param srcDocType   源单据类型(inbound | outbound)
 * @param srcDocId     源单据 ID
 * @param srcDocItemId 源单据行 ID
 * @param invoicedAmount 开票额(正票必须为正)
 * @author inventory
 */
public record InvoiceItemDTO(@NotBlank String srcDocType,
        @NotNull Long srcDocId, @NotNull Long srcDocItemId,
        @NotNull BigDecimal invoicedAmount) {
}
