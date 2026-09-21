package com.company.inventory.model.vo.report;

import java.math.BigDecimal;

/**
 * 对账 SQL 聚合行(供应商/客户维度,数量/金额 BigDecimal 原始值)。
 *
 * @param partyId      供应商或客户 ID
 * @param orderCount   订单数(采购单/销售单)
 * @param orderQty     订单量(行 ordered_qty 合计)
 * @param orderAmount  订单金额(行价税合计合计)
 * @param returnQty    退货量(退货行 quantity 合计)
 * @param returnAmount 退货金额(退货行价税合计合计)
 * @author inventory
 */
public record ReconAggRow(Long partyId, Long orderCount, BigDecimal orderQty,
                          BigDecimal orderAmount, BigDecimal returnQty, BigDecimal returnAmount) {
}
