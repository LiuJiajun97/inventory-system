package com.company.inventory.vo.inbound;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单行出参(契约:quantity 为字符串,serialNos 为 JSON 字符串或 null;一期增量字段)。
 *
 * @param id         主键
 * @param docId      单据 ID
 * @param itemId     物品 ID
 * @param quantity   数量(字符串)
 * @param batchId    批次 ID
 * @param locationId 库位 ID
 * @param serialNos  序列号 JSON 字符串
 * @param unitPrice  入库不含税单价(增量)
 * @param taxRate    税率(增量)
 * @param batchNo    批次号(增量)
 * @param productionDate 生产日期(增量)
 * @param expiryDate 到期日期(增量)
 * @param refLineId  关联采购订单行 ID(增量)
 * @author inventory
 */
public record InboundDocItemVO(Long id, Long docId, Long itemId, String quantity,
        Long batchId, Long locationId, String serialNos,
        BigDecimal unitPrice, BigDecimal taxRate, String batchNo,
        LocalDate productionDate, LocalDate expiryDate, Long refLineId) {
}
