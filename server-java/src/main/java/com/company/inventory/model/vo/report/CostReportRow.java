package com.company.inventory.model.vo.report;

/**
 * 库存成本(移动均价)报表行(成本单元 = 仓库 + 物品 + 批次,批次 0 表示无批次单元)。
 *
 * <p>数量/均价/金额均为字符串契约:数量走合同格式,均价展示 4 位小数
 * (2.625 这类均价 2 位会失真),金额展示 2 位小数。</p>
 *
 * @param warehouseId   仓库 ID
 * @param warehouseName 仓库名称
 * @param itemId        物品 ID
 * @param itemCode      物品编码
 * @param itemName      物品名称
 * @param unit          单位
 * @param batchId       批次 ID(0 表示无批次单元)
 * @param batchNo       批次号(可空,无批次单元为 null)
 * @param quantity      回放截止时数量(字符串)
 * @param avgPrice      移动均价(字符串,4 位小数)
 * @param amount        成本金额(字符串,2 位小数)
 * @author inventory
 */
public record CostReportRow(Long warehouseId, String warehouseName, Long itemId,
                            String itemCode, String itemName, String unit,
                            Long batchId, String batchNo,
                            String quantity, String avgPrice, String amount) {
}
