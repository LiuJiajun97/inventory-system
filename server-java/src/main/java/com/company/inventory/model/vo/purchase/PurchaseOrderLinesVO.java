package com.company.inventory.model.vo.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单明细行出参(单据行拍平:行字段 + 主表关键字段)。
 *
 * @author inventory
 */
public record PurchaseOrderLinesVO(
        Long id, Long docId, String docNo,
        LocalDate docDate, String status, String supplierName,
        Integer lineNo, Long itemId, String itemCode,
        String itemName, String spec, String unit,
        BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate,
        BigDecimal amount, BigDecimal taxAmount, BigDecimal taxInclusiveTotal) {
}
