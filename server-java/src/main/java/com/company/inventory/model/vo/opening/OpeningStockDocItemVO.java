package com.company.inventory.model.vo.opening;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期初单行出参(契约:quantity 为字符串)。
 *
 * @param id             主键
 * @param docId          单据 ID
 * @param lineNo         行号
 * @param itemId         物品 ID
 * @param itemCode       物品编码(详情展示)
 * @param itemName       物品名称(详情展示)
 * @param specSnapshot   规格快照
 * @param unit           单位快照
 * @param quantity       数量(字符串)
 * @param unitPrice      期初成本参考单价(可空)
 * @param batchNo        批次号
 * @param productionDate 生产日期
 * @param expiryDate     保质期到期日
 * @param locationId     库位 ID
 * @author inventory
 */
public record OpeningStockDocItemVO(Long id, Long docId, Integer lineNo, Long itemId,
        String itemCode, String itemName, String specSnapshot, String unit, String quantity,
        BigDecimal unitPrice, String batchNo, LocalDate productionDate, LocalDate expiryDate,
        Long locationId) {
}
