package com.company.inventory.model.vo.stocktake;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 盘点单明细行出参(单据行拍平:行字段 + 主表关键字段)。
 *
 * @author inventory
 */
public record StocktakeDocLineVO(
        Long id, Long docId, String docNo,
        LocalDate docDate, String status, String warehouseName,
        Integer lineNo, Long itemId, String itemCode,
        String itemName, String spec, String unit,
        BigDecimal bookQty, BigDecimal actualQty, BigDecimal diffQty) {
}
