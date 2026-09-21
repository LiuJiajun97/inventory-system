package com.company.inventory.model.vo.report;

import java.time.LocalDate;

/**
 * 库龄/呆滞报表出参(批次维度;无批次仓为 item+仓库 汇总行)。
 *
 * @param warehouseId    仓库 ID
 * @param warehouseName  仓库名称
 * @param itemId         物品 ID
 * @param itemCode       物品编码
 * @param itemName       物品名称
 * @param unit           单位
 * @param batchId        批次 ID(0 表示无批次行)
 * @param batchNo        批次号(可空,无批次行为 null)
 * @param productionDate 生产日期(批次行取批次表;无批次行取该仓最早入方向流水日期,可空)
 * @param ageDays        库龄天数(生产时间为 null 时为 null)
 * @param ageBucket      库龄区间(0-30/31-90/91-180/>180,未知)
 * @param quantity       当前量(字符串)
 * @param stagnant       是否呆滞(该物品+仓库 N 天内无出方向流水且当前量 > 0)
 * @author inventory
 */
public record StockAgeingVO(Long warehouseId, String warehouseName, Long itemId, Long batchId,
                            String itemCode, String itemName, String unit, String batchNo,
                            LocalDate productionDate,
                            Integer ageDays, String ageBucket, String quantity, Boolean stagnant) {
}
