package com.company.inventory.model.vo.report;

import java.util.List;

/**
 * 进销存月报出参(item 维度,跨仓汇总,数量/金额统一字符串)。
 *
 * <p>口径:期初量 = 期初前最后一次流水的 after_qty(无流水为 0);
 * 期末量 = 期末后最后一次流水的 after_qty;入/出量 = 区间内按方向 Σ|change_qty|
 * (pre_alloc 不计);期初 + 入 - 出 = 期末(交叉核对);
 * 金额取单据行价税合计快照,无快照为 null。</p>
 *
 * @param itemId      物品 ID
 * @param itemCode    物品编码
 * @param itemName    物品名称
 * @param unit        单位
 * @param spec        规格(可空)
 * @param openingQty  期初量(字符串)
 * @param inQty       本期入量(字符串)
 * @param outQty      本期出量(字符串)
 * @param closingQty  期末量(字符串)
 * @param inAmount    入库金额(含税,字符串,无快照为 null)
 * @param outAmount   出库金额(含税,字符串,无快照为 null)
 * @param details     各仓期末明细(行展开用)
 * @author inventory
 */
public record StockMonthlyVO(Long itemId, String itemCode, String itemName, String unit,
                             String spec, String openingQty, String inQty, String outQty,
                             String closingQty, String inAmount, String outAmount,
                             List<MonthlyWarehouseDetailVO> details) {
}
