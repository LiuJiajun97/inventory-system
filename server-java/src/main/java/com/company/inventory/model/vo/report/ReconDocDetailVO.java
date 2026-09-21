package com.company.inventory.model.vo.report;

import java.time.LocalDate;

/**
 * 对账行展开的单据明细出参(期间内订单/退货单)。
 *
 * @param docNo     单号
 * @param docDate   单据日期
 * @param docType   单据类型(purchase/purchase_return/sales/sales_return)
 * @param docTypeName 单据类型中文名(采购订单/采购退货/销售订单/销售退货)
 * @param amount    价税合计金额(字符串,可空)
 * @author inventory
 */
public record ReconDocDetailVO(String docNo, LocalDate docDate, String docType,
                               String docTypeName, String amount) {
}
