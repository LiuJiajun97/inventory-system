package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;

/**
 * 发票级累计已核销额 VO(防超核硬校验用)。
 *
 * @param invoiceId     发票 ID
 * @param settledAmount 累计已核销额(未作废付款/收款单合计)
 * @author inventory
 */
public record SettledInvoiceVO(Long invoiceId, BigDecimal settledAmount) {
}
