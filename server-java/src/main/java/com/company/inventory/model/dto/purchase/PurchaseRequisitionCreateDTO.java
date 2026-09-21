package com.company.inventory.model.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建/编辑请购单入参(V25,无金额合计,行价可空不汇总)。
 *
 * @param docDate      单据日期
 * @param warehouseId  收货仓库 ID(转采购订单时沿用)
 * @param applicantId  申请人用户 ID
 * @param department   申请部门(字典 dept 类型的 dict_key,可空)
 * @param remark       备注
 * @param items        请购单行(至少 1 行)
 * @author inventory
 */
public record PurchaseRequisitionCreateDTO(
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotNull(message = "收货仓库 ID 必填")
        @Positive(message = "收货仓库 ID 必须为正数") Long warehouseId,
        @NotNull(message = "申请人 ID 必填")
        @Positive(message = "申请人 ID 必须为正数") Long applicantId,
        String department,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<PurchaseRequisitionLineDTO> items) {
}