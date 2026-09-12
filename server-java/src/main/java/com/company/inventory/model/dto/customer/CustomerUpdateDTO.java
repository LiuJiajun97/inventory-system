package com.company.inventory.model.dto.customer;

import java.math.BigDecimal;

/**
 * 编辑客户入参(编码不可改,字段可空=不更新)。
 *
 * @param customerName           客户名称
 * @param taxNo          税号
 * @param defaultTaxRate 默认税率(百分数)
 * @param contact        联系人
 * @param phone          联系电话
 * @param address        地址
 * @param settleMethod   结算方式
 * @param payTermDays    账期天数
 * @param bankName       开户行
 * @param bankAccount    银行账号
 * @param creditLimit    信用额度
 * @param deliveryAddress 交货地址
 * @param email        邮箱
 * @param status         状态,1 启用 0 停用
 * @param remark         备注
 * @author inventory
 */
public record CustomerUpdateDTO(
        String customerName,
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
        Integer status,
        String remark) {

    /** 兼容旧签名构造器(V9/V10 新增字段默认 null,既有调用/测试不受影响)。 */
    public CustomerUpdateDTO(String customerName, String taxNo, BigDecimal defaultTaxRate,
            String contact, String phone, String address, String settleMethod,
            Integer payTermDays, Integer status, String remark) {
        this(customerName, taxNo, defaultTaxRate, contact, phone, address, settleMethod,
                payTermDays, null, null, null, null, null, status, remark);
    }

    /** 兼容 V9 签名构造器(V10 新增 email 默认 null)。 */
    public CustomerUpdateDTO(String customerName, String taxNo, BigDecimal defaultTaxRate,
            String contact, String phone, String address, String settleMethod,
            Integer payTermDays, String bankName, String bankAccount,
            BigDecimal creditLimit, String deliveryAddress, Integer status, String remark) {
        this(customerName, taxNo, defaultTaxRate, contact, phone, address, settleMethod,
                payTermDays, bankName, bankAccount, creditLimit, deliveryAddress, null, status, remark);
    }
}
