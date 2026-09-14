package com.company.inventory.model.vo.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单明细行出参(单据行拍平:行字段 + 主表关键字段)。
 *
 * @author inventory
 */
public record InboundDocLineVO(
        Long id, Long docId, String docNo,
        LocalDate docDate, String status, String warehouseName,
        Integer lineNo, Long itemId, String itemCode,
        String itemName, String spec, String unit,
        BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxPrice, BigDecimal taxRate,
        BigDecimal amount, BigDecimal taxAmount, BigDecimal taxInclusiveTotal,
        String batchNo) {
}
