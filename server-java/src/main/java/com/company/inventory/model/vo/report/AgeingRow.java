package com.company.inventory.model.vo.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 库龄/呆滞 SQL 行(批次维度;无批次仓 batch_id=0 即 item+仓库 汇总行)。
 *
 * @param warehouseId   仓库 ID
 * @param itemId        物品 ID
 * @param batchId       批次 ID(0 表示无批次行)
 * @param batchNo       批次号(可空,无批次行为 null)
 * @param prodDate      生产日期(批次行取 batch.production_date;
 *                      无批次行取该仓最早入方向流水日期,可空)
 * @param ageDays       库龄天数(生产时间为 null 时为 null)
 * @param qty           当前量
 * @param stagnant      是否呆滞(该物品+仓库 N 天内无出方向流水且当前量 > 0)
 * @author inventory
 */
public record AgeingRow(Long warehouseId, Long itemId, Long batchId, String batchNo,
                        LocalDate prodDate, Integer ageDays, BigDecimal qty, Boolean stagnant) {
}
