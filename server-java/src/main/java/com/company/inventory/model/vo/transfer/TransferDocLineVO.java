package com.company.inventory.model.vo.transfer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 调拨单明细行出参(单据行拍平:行字段 + 主表关键字段)。
 *
 * @author inventory
 */
public record TransferDocLineVO(
        Long id, Long docId, String docNo,
        LocalDate docDate, String status, String fromWarehouseName,
        String toWarehouseName, Integer lineNo, Long itemId,
        String itemCode, String itemName, String spec,
        String unit, BigDecimal quantity, BigDecimal unitPrice) {
}
