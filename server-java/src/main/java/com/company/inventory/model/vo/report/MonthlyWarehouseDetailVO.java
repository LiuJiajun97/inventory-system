package com.company.inventory.model.vo.report;

/**
 * 月报行展开的各仓期末明细出参。
 *
 * @param warehouseId  仓库 ID
 * @param warehouseName 仓库名称
 * @param closingQty   该仓期末量(字符串,契约要求)
 * @author inventory
 */
public record MonthlyWarehouseDetailVO(Long warehouseId, String warehouseName, String closingQty) {
}
