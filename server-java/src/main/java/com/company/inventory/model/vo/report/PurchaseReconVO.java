package com.company.inventory.model.vo.report;

import java.util.List;

/**
 * 采购对账出参(供应商维度,数量/金额统一字符串)。
 *
 * <p>口径:期间内(按 doc_date)采购单数/采购量(行 ordered_qty 合计)/
 * 采购金额(行价税合计合计);退货量/退货金额同理;净采购 = 采购 - 退货;
 * 行展开为期间内采购单与退货单明细。</p>
 *
 * @param supplierId   供应商 ID
 * @param supplierCode 供应商编码
 * @param supplierName 供应商名称
 * @param orderCount   采购单数
 * @param orderQty     采购量(字符串)
 * @param orderAmount  采购金额(价税合计,字符串)
 * @param returnQty    退货量(字符串)
 * @param returnAmount 退货金额(字符串)
 * @param netQty       净采购量(字符串)
 * @param netAmount    净采购金额(字符串)
 * @param details      期间内采购单+退货单明细(行展开用)
 * @author inventory
 */
public record PurchaseReconVO(Long supplierId, String supplierCode, String supplierName,
                              Long orderCount, String orderQty, String orderAmount,
                              String returnQty, String returnAmount, String netQty,
                              String netAmount, List<ReconDocDetailVO> details) {
}
