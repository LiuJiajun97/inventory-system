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
 * @param bankName       开户行(可空)
 * @param bankAccount    银行账号(可空)
 * @param creditLimit    信用额度(可空)
 * @param deliveryAddress 交货地址(可空)
 * @param email        邮箱(可空)
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
        String bankName,
        String bankAccount,
        BigDecimal creditLimit,
        String deliveryAddress,
        String email,
        String remark) {

    /** 兼容旧签名构造器(V9/V10 新增字段默认 null,既有调用/测试不受影响)。 */
    public CustomerCreateDTO(String customerCode, String customerName, String taxNo,
            BigDecimal defaultTaxRate, String contact, String phone, String address,
            String settleMethod, Integer payTermDays, String remark) {
        this(customerCode, customerName, taxNo, defaultTaxRate, contact, phone, address,
                settleMethod, payTermDays, null, null, null, null, null, remark);
    }

    /** 兼容 V9 签名构造器(V10 新增 email 默认 null)。 */
    public CustomerCreateDTO(String customerCode, String customerName, String taxNo,
            BigDecimal defaultTaxRate, String contact, String phone, String address,
            String settleMethod, Integer payTermDays, String bankName, String bankAccount,
            BigDecimal creditLimit, String deliveryAddress, String remark) {
        this(customerCode, customerName, taxNo, defaultTaxRate, contact, phone, address,
                settleMethod, payTermDays, bankName, bankAccount, creditLimit, deliveryAddress,
                null, remark);
    }
}
