package com.company.inventory.model.vo.report;

import java.math.BigDecimal;

/**
 * 月报 SQL 聚合行(item 维度,数量/金额 BigDecimal 原始值)。
 *
 * @param itemId       物品 ID
 * @param openingQty   期初量(Σ 期初前最后一次流水 after_qty)
 * @param closingQty   期末量(Σ 期末后最后一次流水 after_qty)
 * @param inQty        本期入量(入方向 Σ change_qty)
 * @param outQty       本期出量(出方向 -Σ change_qty)
 * @param inAmount     入库金额(含税,无快照为 null)
 * @param outAmount    出库金额(含税,无快照为 null)
 * @author inventory
 */
public record MonthlyAggRow(Long itemId, BigDecimal openingQty, BigDecimal closingQty,
                            BigDecimal inQty, BigDecimal outQty,
                            BigDecimal inAmount, BigDecimal outAmount) {
}
