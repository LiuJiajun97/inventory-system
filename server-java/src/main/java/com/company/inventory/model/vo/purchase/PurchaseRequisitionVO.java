package com.company.inventory.model.vo.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 请购单出参(V25):表头 + 行(无金额合计)。
 *
 * @author inventory
 */
public record PurchaseRequisitionVO(Long id, String docNo, LocalDate docDate,
        Long warehouseId, Long applicantId, String department,
        String status, String creator, LocalDateTime createdAt,
        String updater, LocalDateTime updatedAt, String remark,
        List<PurchaseRequisitionItemVO> items) {
}