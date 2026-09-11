package com.company.inventory.dto.location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 新建库位入参。
 *
 * @param warehouseId  仓库 ID
 * @param locationCode 库位编码
 * @param locationName 库位名称
 * @author inventory
 */
public record LocationCreateDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        @NotBlank(message = "库位编码必填") String locationCode,
        String locationName) {
}
