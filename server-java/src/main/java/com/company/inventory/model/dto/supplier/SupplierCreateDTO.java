package com.company.inventory.model.dto.supplier;

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
 * @param bankName       开户行(可空)
 * @param bankAccount    银行账号(可空)
 * @param creditLimit    信用额度(可空)
 * @param deliveryAddress 交货地址(可空)
 * @param email        邮箱(可空)
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
        String bankName,
        String bankAccount,
        BigDecimal creditLimit,
        String deliveryAddress,
        String email,
        String remark) {

    /** 兼容旧签名构造器(V9/V10 新增字段默认 null,既有调用/测试不受影响)。 */
    public SupplierCreateDTO(String supplierCode, String supplierName, String taxNo,
            BigDecimal defaultTaxRate, String contact, String phone, String address,
            String settleMethod, Integer payTermDays, String remark) {
        this(supplierCode, supplierName, taxNo, defaultTaxRate, contact, phone, address,
                settleMethod, payTermDays, null, null, null, null, null, remark);
    }

    /** 兼容 V9 签名构造器(V10 新增 email 默认 null)。 */
    public SupplierCreateDTO(String supplierCode, String supplierName, String taxNo,
            BigDecimal defaultTaxRate, String contact, String phone, String address,
            String settleMethod, Integer payTermDays, String bankName, String bankAccount,
            BigDecimal creditLimit, String deliveryAddress, String remark) {
        this(supplierCode, supplierName, taxNo, defaultTaxRate, contact, phone, address,
                settleMethod, payTermDays, bankName, bankAccount, creditLimit, deliveryAddress,
                null, remark);
    }
}
