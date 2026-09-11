package com.company.inventory.model.dto.adjust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建库存调整单入参(盘盈 gain/盘亏 loss/报损 scrap;审批通过时执行库存动作)。
 *
 * @param warehouseId 仓库 ID
 * @param docDate     单据日期
 * @param adjustType  调整类型(gain/loss/scrap)
 * @param refDocNo    来源盘点单号(可空,盘点差异生成时携带)
 * @param remark      备注
 * @param items       调整行(至少 1 行)
 * @author inventory
 */
public record StockAdjustCreateDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotBlank(message = "调整类型必填") String adjustType,
        String refDocNo,
        String remark,
        @NotEmpty(message = "至少 1 行") List<StockAdjustLineDTO> items) {
}
