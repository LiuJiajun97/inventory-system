package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;

/**
 * 发票行 VO(详情行表,含源单据号与差异额)。
 *
 * @param id             行 ID
 * @param invoiceId      发票 ID
 * @param lineNo         行号
 * @param srcDocType     源单据类型(inbound | outbound | purchase_return | sales_return)
 * @param srcDocNo       源单据号(联表展示)
 * @param srcDocId       源单据 ID
 * @param srcDocItemId   源单据行 ID
 * @param itemId         物品 ID
 * @param itemCode       物品编码
 * @param itemName       物品名称
 * @param specSnapshot   规格快照
 * @param unit           单位
 * @param quantity       数量
 * @param invoicedAmount 开票额(负票为负)
 * @param srcAmount      源行含税额快照(负票为负)
 * @param variance       差异额(开票额 - 源行含税额)
 * @param sign           正负号
 * @param batchNo        批次号
 * @author inventory
 */
public record InvoiceItemVO(Long id, Long invoiceId, Integer lineNo, String srcDocType,
        String srcDocNo, Long srcDocId, Long srcDocItemId, Long itemId, String itemCode,
        String itemName, String specSnapshot, String unit, BigDecimal quantity,
        BigDecimal invoicedAmount, BigDecimal srcAmount, BigDecimal variance,
        String sign, String batchNo) {
}
