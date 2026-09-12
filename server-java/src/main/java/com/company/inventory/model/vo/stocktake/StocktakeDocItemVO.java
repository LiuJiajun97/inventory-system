package com.company.inventory.model.vo.stocktake;

import java.time.LocalDate;

/**
 * 盘点单行出参(bookQty/actualQty/diffQty 均为字符串,未盘为 null)。
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
 * @param bookQty      账面快照(字符串)
 * @param actualQty    实盘数量(字符串,未盘 null)
 * @param diffQty      差异数量(字符串,未盘 null)
 * @param checkerName  盘点人(V9 增量)
 * @param checkDate    盘点日期(V9 增量)
 * @author inventory
 */
public record StocktakeDocItemVO(Long id, Integer lineNo, Long itemId, String itemCode,
        String itemName, String specSnapshot, String unit, Long batchId, Long locationId,
        String bookQty, String actualQty, String diffQty, String checkerName,
        LocalDate checkDate) {
}
