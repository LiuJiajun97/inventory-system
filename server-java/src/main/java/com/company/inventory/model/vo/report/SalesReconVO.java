package com.company.inventory.model.vo.report;

import java.util.List;

/**
 * 销售对账出参(客户维度,数量/金额统一字符串)。
 *
 * <p>口径:期间内(按 doc_date)销售单数/销售量(行 ordered_qty 合计)/
 * 销售金额(行价税合计合计);退货量/退货金额同理;净销售 = 销售 - 退货;
 * 行展开为期间内销售单与退货单明细。</p>
 *
 * @param customerId   客户 ID
 * @param customerCode 客户编码
 * @param customerName 客户名称
 * @param orderCount   销售单数
 * @param orderQty     销售量(字符串)
 * @param orderAmount  销售金额(价税合计,字符串)
 * @param returnQty    退货量(字符串)
 * @param returnAmount 退货金额(字符串)
 * @param netQty       净销售量(字符串)
 * @param netAmount    净销售金额(字符串)
 * @param details      期间内销售单+退货单明细(行展开用)
 * @author inventory
 */
public record SalesReconVO(Long customerId, String customerCode, String customerName,
                           Long orderCount, String orderQty, String orderAmount,
                           String returnQty, String returnAmount, String netQty,
                           String netAmount, List<ReconDocDetailVO> details) {
}
