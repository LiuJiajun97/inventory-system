package com.company.inventory.model.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建/编辑销售报价单入参(V25,服务端价税重算,不信前端金额)。
 *
 * @param docDate          单据日期
 * @param customerId       客户 ID
 * @param salespersonId    销售员用户 ID(可空,报价环节可不指定)
 * @param warehouseId      发货仓库 ID(转销售订单时沿用)
 * @param quoteValidUntil  报价有效期(可空,过期仅展示标记)
 * @param remark           备注
 * @param items            报价单行(至少 1 行)
 * @author inventory
 */
public record SalesQuotationCreateDTO(
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotNull(message = "客户 ID 必填")
        @Positive(message = "客户 ID 必须为正数") Long customerId,
        @Positive(message = "销售员 ID 必须为正数") Long salespersonId,
        @NotNull(message = "发货仓库 ID 必填")
        @Positive(message = "发货仓库 ID 必须为正数") Long warehouseId,
        LocalDate quoteValidUntil,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<SalesQuotationLineDTO> items) {
}