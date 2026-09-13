package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;

/**
 * 源单据行累计已开票额 VO(防超开硬校验用)。
 *
 * @param srcDocType   源单据类型
 * @param srcDocId     源单据 ID
 * @param srcDocItemId 源单据行 ID
 * @param invoiced     该行累计已开票额(未作废发票全部行合计,正负号随行)
 * @author inventory
 */
public record InvoicedSrcVO(String srcDocType, Long srcDocId, Long srcDocItemId,
        BigDecimal invoiced) {
}
