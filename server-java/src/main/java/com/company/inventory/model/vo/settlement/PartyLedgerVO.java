package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;

/**
 * 台账主行 VO(应付/应收台账按对方聚合,零建表实时聚合)。
 *
 * @param partyId      对方 ID(供应商/客户)
 * @param partyName    对方名称
 * @param receivedAmount 采购入库(销售出库)关联额(台账"入库额/出库额"列)
 * @param invoicedAmount 已开票净额(confirmed 发票合计,负票冲减)
 * @param settledAmount  已付款(收款)额(confirmed 核销行合计)
 * @author inventory
 */
public record PartyLedgerVO(Long partyId, String partyName, BigDecimal receivedAmount,
        BigDecimal invoicedAmount, BigDecimal settledAmount) {
}
