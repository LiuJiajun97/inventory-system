package com.company.inventory.vo.outbound;

import java.math.BigDecimal;

/**
 * 出库单行出参(契约:quantity 为字符串,serialNos 为 JSON 字符串或 null;一期增量字段)。
 *
 * @param id         主键
 * @param docId      单据 ID
 * @param itemId     物品 ID
 * @param quantity   数量(字符串)
 * @param batchId    批次 ID
 * @param locationId 库位 ID
 * @param serialNos  序列号 JSON 字符串
 * @param unitPrice  出库参考单价(增量)
 * @param refLineId  关联销售订单行 ID(增量)
 * @author inventory
 */
public record OutboundDocItemVO(Long id, Long docId, Long itemId, String quantity,
        Long batchId, Long locationId, String serialNos, BigDecimal unitPrice, Long refLineId) {
}
