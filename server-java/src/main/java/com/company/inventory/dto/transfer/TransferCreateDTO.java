package com.company.inventory.dto.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建/编辑调拨单入参(源扣目的加原子一步,表头合计服务端重算)。
 *
 * @param docDate         单据日期
 * @param fromWarehouseId 源仓库 ID
 * @param toWarehouseId   目的仓库 ID
 * @param remark          备注
 * @param items           调拨行(至少 1 行)
 * @author inventory
 */
public record TransferCreateDTO(
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotNull(message = "源仓库 ID 必填")
        @Positive(message = "源仓库 ID 必须为正数") Long fromWarehouseId,
        @NotNull(message = "目的仓库 ID 必填")
        @Positive(message = "目的仓库 ID 必须为正数") Long toWarehouseId,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<TransferLineDTO> items) {
}
