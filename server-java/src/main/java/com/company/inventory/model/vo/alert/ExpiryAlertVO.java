package com.company.inventory.model.vo.alert;

import java.time.LocalDate;

/**
 * 临期预警行出参(启用保质期批次,到期日在 N 天内,可用量大于 0)。
 *
 * @param itemId       物品 ID
 * @param itemCode     物品编码
 * @param itemName     物品名称
 * @param unit         单位
 * @param batchNo      批次号
 * @param warehouseId  仓库 ID
 * @param warehouseName 仓库名称
 * @param productionDate 生产日期
 * @param expiryDate   到期日期
 * @param daysLeft     距到期天数(可负=已过期)
 * @param quantity     库存数量(字符串)
 * @param availableQty 可用数量(字符串)
 * @author inventory
 */
public record ExpiryAlertVO(Long itemId, String itemCode, String itemName, String unit,
        String batchNo, Long warehouseId, String warehouseName,
        LocalDate productionDate, LocalDate expiryDate, Long daysLeft,
        String quantity, String availableQty) {
}
