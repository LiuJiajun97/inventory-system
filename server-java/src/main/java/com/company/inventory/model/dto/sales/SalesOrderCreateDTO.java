package com.company.inventory.model.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建/编辑销售订单入参(服务端重算行金额与表头三合计)。
 *
 * @param docDate       单据日期
 * @param customerId    客户 ID
 * @param salespersonId 销售员用户 ID
 * @param warehouseId   发货仓库 ID(审批时按此仓 FEFO 预占)
 * @param remark        备注
 * @param items         订单行(至少 1 行)
 * @author inventory
 */
public record SalesOrderCreateDTO(
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotNull(message = "客户 ID 必填")
        @Positive(message = "客户 ID 必须为正数") Long customerId,
        @NotNull(message = "销售员 ID 必填")
        @Positive(message = "销售员 ID 必须为正数") Long salespersonId,
        @NotNull(message = "发货仓库 ID 必填")
        @Positive(message = "发货仓库 ID 必须为正数") Long warehouseId,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<SalesOrderLineDTO> items) {
}
