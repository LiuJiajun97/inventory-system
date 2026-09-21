package com.company.inventory.model.vo.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请购单行出参(V25,无金额合计,行价可空)。
 *
 * @author inventory
 */
public record PurchaseRequisitionItemVO(Long id, Integer lineNo, Long itemId,
        String itemCode, String itemName, String unit,
        String quantity,
        LocalDate expectedDate,
        BigDecimal unitPrice,
        String remark) {
}