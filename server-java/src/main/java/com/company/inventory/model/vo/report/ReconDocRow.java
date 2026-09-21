package com.company.inventory.model.vo.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对账行展开的单据明细 SQL 行(期间内该供应商/客户的订单与退货单)。
 *
 * @param partyId  供应商或客户 ID
 * @param docNo    单号
 * @param docDate  单据日期
 * @param docType  单据类型(purchase/purchase_return/sales/sales_return)
 * @param amount   价税合计金额
 * @author inventory
 */
public record ReconDocRow(Long partyId, String docNo, LocalDate docDate, String docType, BigDecimal amount) {
}
