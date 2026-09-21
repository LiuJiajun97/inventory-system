package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 可挂票源单据行 VO(新建发票选择区:未开票/部分开票的单据行)。
 *
 * @param srcDocType   源单据类型(inbound 采购关联入库行 | outbound 销售关联出库行)
 * @param srcDocId     源单据 ID
 * @param srcDocItemId 源单据行 ID
 * @param srcDocNo     源单据号
 * @param docDate      源单据日期
 * @param itemId       物品 ID
 * @param itemCode     物品编码
 * @param itemName     物品名称
 * @param spec         规格
 * @param unit         单位
 * @param quantity     源行数量
 * @param srcAmount    源行含税额
 * @param invoicedSoFar 该行累计已开票额(未作废发票合计)
 * @param remaining    剩余可开额(含税额 - 累计已开票)
 * @author inventory
 */
public record InvoiceableLineVO(String srcDocType, Long srcDocId, Long srcDocItemId,
        String srcDocNo, LocalDate docDate, Long itemId, String itemCode, String itemName,
        String spec, String unit, BigDecimal quantity, BigDecimal srcAmount,
        BigDecimal invoicedSoFar, BigDecimal remaining) {
}
