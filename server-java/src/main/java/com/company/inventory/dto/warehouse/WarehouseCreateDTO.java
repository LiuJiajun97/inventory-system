package com.company.inventory.dto.warehouse;
import jakarta.validation.constraints.NotBlank;

/**
 * 新建仓库入参(4 开关可选,缺省 false)。
 *
 * @param warehouseCode  仓库编码
 * @param warehouseName  仓库名称
 * @param warehouseType  仓库类型
 * @param enableBatch    启用批次
 * @param enableExpiry   启用保质期
 * @param enableSerial   启用序列号
 * @param enableLocation 启用库位
 * @author inventory
 */
public record WarehouseCreateDTO(
        @NotBlank(message = "仓库编码必填") String warehouseCode,
        @NotBlank(message = "仓库名称必填") String warehouseName,
        @NotBlank(message = "仓库类型必填") String warehouseType,
        Boolean enableBatch,
        Boolean enableExpiry,
        Boolean enableSerial,
        Boolean enableLocation) {
}
