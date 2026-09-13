package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 未核销发票 VO(新建付款/收款核销行选择区:confirmed 正票剩余可核额)。
 *
 * @param invoiceId      发票 ID
 * @param docNo          发票号
 * @param invoiceDate    发票日期
 * @param totalAmount    票额
 * @param settledAmount  累计已核销额
 * @param remainingAmount 剩余可核额
 * @author inventory
 */
public record UnsettledInvoiceVO(Long invoiceId, String docNo, LocalDate invoiceDate,
        BigDecimal totalAmount, BigDecimal settledAmount, BigDecimal remainingAmount) {
}
