package com.company.inventory.model.vo.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售订单行出参。
 *
 * @param id                     行 ID
 * @param lineNo                 行号
 * @param itemId                 物品 ID
 * @param itemCode               物品编码
 * @param itemName               物品名称
 * @param specSnapshot           规格快照
 * @param unit                   单位快照
 * @param orderedQty             订购数量(字符串)
 * @param shippedQty             累计发货数量(字符串)
 * @param returnedQty            累计退货数量(字符串,可退上限=shipped-returned)
 * @param customerDeliveryDate   客户要货日
 * @param unitPrice              不含税单价
 * @param taxRate                税率
 * @param amount                 不含税金额(字符串)
 * @param taxAmount              税额(字符串)
 * @param taxInclusiveTotal      价税合计(字符串)
 * @param closed                 行是否关闭
 * @param lineRemark             行备注
 * @author inventory
 */
public record SalesOrderItemVO(Long id, Integer lineNo, Long itemId, String itemCode,
        String itemName, String specSnapshot, String unit,
        String orderedQty, String shippedQty, String returnedQty,
        LocalDate customerDeliveryDate,
        BigDecimal unitPrice, BigDecimal taxPrice, BigDecimal taxRate, String amount, String taxAmount,
        String taxInclusiveTotal, Boolean closed, String lineRemark) {
}
