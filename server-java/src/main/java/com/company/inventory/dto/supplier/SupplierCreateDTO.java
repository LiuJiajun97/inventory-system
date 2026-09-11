package com.company.inventory.dto.supplier;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 新建供应商入参。
 *
 * @param supplierCode           供应商编码
 * @param supplierName           供应商名称
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
public record SupplierCreateDTO(
        @NotBlank(message = "供应商编码必填") String supplierCode,
        @NotBlank(message = "供应商名称必填") String supplierName,
        String taxNo,
        BigDecimal defaultTaxRate,
        String contact,
        String phone,
        String address,
        String settleMethod,
        Integer payTermDays,
        String remark) {
}
