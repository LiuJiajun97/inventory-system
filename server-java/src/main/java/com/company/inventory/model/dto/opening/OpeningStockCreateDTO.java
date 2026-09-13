package com.company.inventory.model.dto.opening;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建期初单入参(create 即过账,服务端构造入库单走现有入库链路)。
 *
 * @param warehouseId 仓库 ID
 * @param docDate     单据日期
 * @param remark      备注
 * @param items       期初行(至少 1 行)
 * @author inventory
 */
public record OpeningStockCreateDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        @NotNull(message = "单据日期必填") LocalDate docDate,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<OpeningStockLineDTO> items) {
}
