package com.company.inventory.dto.transfer;

/**
 * 调拨单审批动作入参(驳回必填原因)。
 *
 * @param rejectReason 驳回原因
 * @author inventory
 */
public record TransferActionDTO(String rejectReason) {
}
