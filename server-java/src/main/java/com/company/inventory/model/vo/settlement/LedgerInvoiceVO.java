package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 台账行展开-发票 VO(某对方 confirmed 发票列表)。
 *
 * @param invoiceId      发票 ID
 * @param docNo          发票号
 * @param invoiceDate    发票日期
 * @param totalAmount    票额(负票为负)
 * @param sign           正负号
 * @param sourceType     来源(manual | return_gen)
 * @param settledAmount  累计已核销额(负票恒 0)
 * @author inventory
 */
public record LedgerInvoiceVO(Long invoiceId, String docNo, LocalDate invoiceDate,
        BigDecimal totalAmount, String sign, String sourceType, BigDecimal settledAmount) {
}
