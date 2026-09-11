package com.company.inventory.vo.alert;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 临期预警查询行(数据库层结果,数量未转契约字符串,内部使用)。
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
 * @param daysLeft     距到期天数(可负)
 * @param quantity     库存数量
 * @param availableQty 可用数量
 * @author inventory
 */
public record ExpiryAlertRow(Long itemId, String itemCode, String itemName, String unit,
        String batchNo, Long warehouseId, String warehouseName,
        LocalDate productionDate, LocalDate expiryDate, Long daysLeft,
        BigDecimal quantity, BigDecimal availableQty) {
}
