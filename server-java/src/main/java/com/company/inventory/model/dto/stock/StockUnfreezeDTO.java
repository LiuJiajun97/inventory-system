package com.company.inventory.model.dto.stock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 库存解冻入参(V26:仓+批次粒度)。
 *
 * @param warehouseId 仓库 ID
 * @param batchNo     批次号
 * @author inventory
 */
public record StockUnfreezeDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        @NotBlank(message = "批次号必填") String batchNo) {
}
