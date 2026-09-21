package com.company.inventory.model.dto.stock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 库存冻结入参(V26:仓+批次粒度,冻结只拦出不拦入)。
 *
 * @param warehouseId 仓库 ID
 * @param batchNo     批次号
 * @param reason      冻结原因(必填)
 * @author inventory
 */
public record StockFreezeDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        @NotBlank(message = "批次号必填") String batchNo,
        @NotBlank(message = "冻结原因必填") String reason) {
}
