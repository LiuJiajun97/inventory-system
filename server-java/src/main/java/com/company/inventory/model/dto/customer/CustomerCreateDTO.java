package com.company.inventory.model.dto.customer;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 新建客户入参。
 *
 * @param customerCode           客户编码
 * @param customerName           客户名称
 * @param taxNo          税号
 * @param defaultTaxRate 默认税率(百分数,可空默认 13)
 * @param contact        联系人
 * @param phone          联系电话
 * @param address        地址
 * @param settleMethod   结算方式
 * @param payTermDays    账期天数
 * @param remark         备注
 * @author inventory
 */
public record CustomerCreateDTO(
        @NotBlank(message = "客户编码必填") String customerCode,
        @NotBlank(message = "客户名称必填") String customerName,
        String taxNo,
        BigDecimal defaultTaxRate,
        String contact,
        String phone,
        String address,
        String settleMethod,
        Integer payTermDays,
        String remark) {
}
