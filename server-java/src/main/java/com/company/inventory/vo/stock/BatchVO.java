package com.company.inventory.vo.stock;
import java.time.LocalDate;

/**
 * 批次出参(库存行内嵌对象,契约:6 字段)。
 *
 * @param id             主键
 * @param itemId         物品 ID
 * @param batchNo        批次号
 * @param productionDate 生产日期
 * @param expiryDate     保质期到期日
 * @param supplier       供应商
 * @param status         批次状态
 * @author inventory
 */
public record BatchVO(Long id, Long itemId, String batchNo, LocalDate productionDate,
        LocalDate expiryDate, String supplier, String status) {
}
