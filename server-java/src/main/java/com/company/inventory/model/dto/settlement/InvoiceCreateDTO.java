package com.company.inventory.model.dto.settlement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建发票入参 DTO(手工登记正票;服务端逐行取源单据行含税额,不信前端)。
 *
 * @param invoiceType 发票类型(purchase | sales)
 * @param partyId     对方 ID(采购票=供应商,销售票=客户)
 * @param invoiceDate 发票日期
 * @param remark      备注
 * @param items       发票行(源单据行引用 + 开票额)
 * @author inventory
 */
public record InvoiceCreateDTO(@NotBlank String invoiceType,
        @NotNull Long partyId, @NotNull LocalDate invoiceDate, String remark,
        @NotEmpty @Valid List<InvoiceItemDTO> items) {
}
