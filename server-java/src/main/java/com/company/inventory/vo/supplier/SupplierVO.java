package com.company.inventory.vo.supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商出参。
 *
 * @param id             主键
 * @param supplierCode           供应商编码
 * @param supplierName           供应商名称
 * @param taxNo          税号
 * @param defaultTaxRate 默认税率
 * @param contact        联系人
 * @param phone          联系电话
 * @param address        地址
 * @param settleMethod   结算方式
 * @param payTermDays    账期天数
 * @param status         状态
 * @param remark         备注
 * @param createdBy      创建人
 * @param createdAt      创建时间
 * @param updatedBy      修改人
 * @param updatedAt      修改时间
 * @author inventory
 */
public record SupplierVO(Long id, String supplierCode, String supplierName, String taxNo,
        BigDecimal defaultTaxRate, String contact, String phone, String address,
        String settleMethod, Integer payTermDays, Integer status, String remark,
        String createdBy, LocalDateTime createdAt, String updatedBy, LocalDateTime updatedAt) {
}
