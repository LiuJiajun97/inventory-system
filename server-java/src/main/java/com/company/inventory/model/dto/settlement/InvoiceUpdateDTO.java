package com.company.inventory.model.dto.settlement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

/**
 * 修改发票入参 DTO(仅 draft/mismatch 可改;行可改/删,正票行可加,负票行不可加)。
 *
 * <p>行语义:正票行传源单据行引用 + 开票额(同新建);负票(退货生成)行必须原样带回
 * srcDocType/srcDocId/srcDocItemId,仅可改开票额,未带回的行视为删除。</p>
 *
 * @param partyId     对方 ID(可空,空则保持原对方)
 * @param invoiceDate 发票日期(可空,空则保持原日期)
 * @param remark      备注(可空)
 * @param items       全量发票行(覆盖式)
 * @author inventory
 */
public record InvoiceUpdateDTO(Long partyId, LocalDate invoiceDate, String remark,
        @NotEmpty @Valid List<InvoiceItemDTO> items) {
}
