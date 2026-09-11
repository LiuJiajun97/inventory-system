package com.company.inventory.model.dto.inbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建入库单入参(手工入库;关联采购订单到货时 refType=purchase + refDocId,行带 refLineId)。
 *
 * @param warehouseId 仓库 ID
 * @param remark      备注
 * @param items       入库行(至少 1 行)
 * @param refType     关联单据类型(可空:purchase=采购到货)
 * @param refDocId    关联采购订单 ID(可空)
 * @param docDate     单据日期(可空)
 * @author inventory
 */
public record InboundCreateDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<InboundLineDTO> items,
        String refType,
        @Positive(message = "关联订单 ID 必须为正数") Long refDocId,
        LocalDate docDate) {
}
