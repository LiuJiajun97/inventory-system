package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;

/**
 * 核销行 VO(付款/收款单详情行表)。
 *
 * @param id         行 ID
 * @param paymentId  付款/收款单 ID
 * @param invoiceId  发票 ID
 * @param invoiceNo  发票号(联表展示)
 * @param amount     核销额(正数)
 * @author inventory
 */
public record PaymentLineVO(Long id, Long paymentId, Long invoiceId, String invoiceNo,
        BigDecimal amount) {
}
