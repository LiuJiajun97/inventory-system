package com.company.inventory.model.vo.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售订单明细行出参(单据行拍平:行字段 + 主表关键字段)。
 *
 * @author inventory
 */
public record SalesOrderLinesVO(
        Long id, Long docId, String docNo,
        LocalDate docDate, String status, String customerName,
        Integer lineNo, Long itemId, String itemCode,
        String itemName, String spec, String unit,
        BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate,
        BigDecimal amount, BigDecimal taxAmount, BigDecimal taxInclusiveTotal) {
}
