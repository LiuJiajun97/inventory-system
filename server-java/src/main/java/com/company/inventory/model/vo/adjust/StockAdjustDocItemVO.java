package com.company.inventory.model.vo.adjust;

import java.math.BigDecimal;

/**
 * 库存调整单行出参。
 *
 * @param id           行 ID
 * @param lineNo       行号
 * @param itemId       物品 ID
 * @param itemCode     物品编码
 * @param itemName     物品名称
 * @param specSnapshot 规格快照
 * @param unit         单位快照
 * @param batchId      批次 ID
 * @param locationId   库位 ID
 * @param qty          调整数量(字符串,绝对值)
 * @param unitPrice    成本参考价
 * @param reason       原因
 * @author inventory
 */
public record StockAdjustDocItemVO(Long id, Integer lineNo, Long itemId, String itemCode,
        String itemName, String specSnapshot, String unit, Long batchId, Long locationId,
        String qty, BigDecimal unitPrice, String reason) {
}
