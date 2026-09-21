package com.company.inventory.model.dto.opening;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期初行入参(序列号物品不支持,批次/库位必填性由服务端按仓库开关校验)。
 *
 * @param itemId         物品 ID
 * @param quantity       期初数量(正数)
 * @param unitPrice      期初成本参考单价(可选,仅快照)
 * @param batchNo        批次号(批次/保质期仓必填)
 * @param productionDate 生产日期
 * @param expiryDate     保质期到期日
 * @param locationId     库位 ID(库位仓必填)
 * @author inventory
 */
public record OpeningStockLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "数量必填")
        @Positive(message = "数量必须为正数") BigDecimal quantity,
        BigDecimal unitPrice,
        String batchNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        @Positive(message = "库位 ID 必须为正数") Long locationId) {
}
