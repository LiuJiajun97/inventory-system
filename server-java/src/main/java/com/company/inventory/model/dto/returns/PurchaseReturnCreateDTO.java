package com.company.inventory.model.dto.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建采购退货单入参(create 即过账,服务端按原单行锁价并重算金额)。
 *
 * @param purchaseOrderId 原采购订单 ID(必须已审批)
 * @param warehouseId     退货仓库 ID
 * @param docDate         单据日期(可空,空取当天)
 * @param remark          备注
 * @param items           退货行(至少 1 行)
 * @author inventory
 */
public record PurchaseReturnCreateDTO(
        @NotNull(message = "原采购订单 ID 必填")
        @Positive(message = "原采购订单 ID 必须为正数") Long purchaseOrderId,
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        LocalDate docDate,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<PurchaseReturnLineDTO> items) {
}
