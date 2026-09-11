package com.company.inventory.model.dto.purchase;

/**
 * 采购订单审批动作入参(驳回必填原因;审批/关闭/作废忽略该字段)。
 *
 * @param rejectReason 驳回原因
 * @author inventory
 */
public record PurchaseActionDTO(String rejectReason) {
}
