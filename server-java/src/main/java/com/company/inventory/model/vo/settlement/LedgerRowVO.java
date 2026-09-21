package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;
import java.util.List;

/**
 * 台账完整行 VO(应付/应收台账返回:主行 + 发票展开 + 订单执行子表)。
 *
 * @param partyId        对方 ID
 * @param partyName      对方名称
 * @param receivedAmount 采购入库(销售出库)关联额
 * @param invoicedAmount 已开票净额
 * @param estimatedAmount 待开票暂估(入库/出库关联额 - 已开票额,单列不计入余额)
 * @param settledAmount  已付款(收款)额
 * @param balance        应付(应收)余额=已开票净额 - 已付款(收款)额
 * @param invoices       confirmed 发票列表(号/日期/额/已核销/未核销/来源)
 * @param orders         订单执行子表(下单/入库/开票/付款四列)
 * @author inventory
 */
public record LedgerRowVO(Long partyId, String partyName, BigDecimal receivedAmount,
        BigDecimal invoicedAmount, BigDecimal estimatedAmount, BigDecimal settledAmount,
        BigDecimal balance, List<LedgerInvoiceVO> invoices, List<OrderProgressVO> orders) {
}
