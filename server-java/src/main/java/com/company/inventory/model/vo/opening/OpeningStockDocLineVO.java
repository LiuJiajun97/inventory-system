package com.company.inventory.model.vo.opening;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期初库存明细行出参(单据行拍平:行字段 + 主表关键字段)。
 *
 * @author inventory
 */
public record OpeningStockDocLineVO(
        Long id, Long docId, String docNo,
        LocalDate docDate, String status, String warehouseName,
        Integer lineNo, Long itemId, String itemCode,
        String itemName, String spec, String unit,
        BigDecimal quantity, BigDecimal unitPrice, String batchNo) {
}
