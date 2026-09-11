package com.company.inventory.model.vo.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户出参。
 *
 * @param id             主键
 * @param customerCode           客户编码
 * @param customerName           客户名称
 * @param taxNo          税号
 * @param defaultTaxRate 默认税率
 * @param contact        联系人
 * @param phone          联系电话
 * @param address        地址
 * @param settleMethod   结算方式
 * @param payTermDays    账期天数
 * @param status         状态
 * @param remark         备注
 * @param creator        创建人
 * @param createdAt      创建时间
 * @param updater        修改人
 * @param updatedAt      修改时间
 * @author inventory
 */
public record CustomerVO(Long id, String customerCode, String customerName, String taxNo,
        BigDecimal defaultTaxRate, String contact, String phone, String address,
        String settleMethod, Integer payTermDays, Integer status, String remark,
        String creator, LocalDateTime createdAt, String updater, LocalDateTime updatedAt) {
}
