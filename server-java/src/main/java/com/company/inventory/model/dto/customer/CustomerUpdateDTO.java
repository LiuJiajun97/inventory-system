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
        Integer status,
        String remark) {
}
