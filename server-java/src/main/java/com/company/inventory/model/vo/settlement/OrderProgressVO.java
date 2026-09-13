package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单执行跟踪 VO(金蝶执行明细表口径,台账行展开子表)。
 *
 * @param orderId      订单 ID(采购订单/销售订单)
 * @param orderNo      订单号
 * @param docDate      订单日期
 * @param orderAmount  下单额(订单含税总额)
 * @param receivedAmount 已入库(出库)额(关联单据行含税额合计)
 * @param invoicedAmount 已开票额(经入库 ref 链 + 票行 src 链聚合,confirmed 净额)
 * @param settledAmount  已付款(收款)额(核销额按该订单行开票占比分摊)
 * @author inventory
 */
public record OrderProgressVO(Long orderId, String orderNo, LocalDate docDate,
        BigDecimal orderAmount, BigDecimal receivedAmount, BigDecimal invoicedAmount,
        BigDecimal settledAmount) {
}
