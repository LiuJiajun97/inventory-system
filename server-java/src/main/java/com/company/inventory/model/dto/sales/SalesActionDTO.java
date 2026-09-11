package com.company.inventory.model.dto.sales;

/**
 * 销售订单审批动作入参(驳回必填原因)。
 *
 * @param rejectReason 驳回原因
 * @author inventory
 */
public record SalesActionDTO(String rejectReason) {
}
