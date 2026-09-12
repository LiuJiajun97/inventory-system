package com.company.inventory.model.vo.returns;

import java.math.BigDecimal;

/**
 * 销售退货单行出参。
 *
 * @param id                  行 ID
 * @param docId               退货单 ID
 * @param lineNo              行号
 * @param salesOrderItemId    原销售订单行 ID
 * @param itemId              物品 ID
 * @param specSnapshot        规格快照
 * @param unit                单位快照
 * @param quantity            退货数量(字符串,契约要求)
 * @param unitPrice           不含税单价(原行快照)
 * @param taxRate             税率(原行快照)
 * @param amount              不含税金额(字符串)
 * @param taxAmount           税额(字符串)
 * @param taxInclusiveTotal   价税合计(字符串)
 * @author inventory
 */
public record SalesReturnItemVO(Long id, Long docId, Integer lineNo,
        Long salesOrderItemId, Long itemId, String specSnapshot, String unit,
        String quantity, BigDecimal unitPrice, BigDecimal taxRate,
        String amount, String taxAmount, String taxInclusiveTotal) {
}
