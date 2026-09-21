package com.company.inventory.model.vo.sales;

import java.math.BigDecimal;

/**
 * 销售报价单行出参(V25,六列金额照抄销售订单行 VO)。
 *
 * @author inventory
 */
public record SalesQuotationItemVO(Long id, Integer lineNo, Long itemId,
        String itemCode, String itemName, String unit,
        String quantity,
        BigDecimal unitPrice, BigDecimal taxPrice, BigDecimal taxRate,
        String amount, String taxAmount, String totalAmount,
        String remark) {
}