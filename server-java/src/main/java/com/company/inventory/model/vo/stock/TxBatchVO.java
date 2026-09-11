package com.company.inventory.model.vo.stock;
/**
 * 流水行内嵌的批次精简对象(契约:仅 id + batchNo)。
 *
 * @param id      主键
 * @param batchNo 批次号
 * @author inventory
 */
public record TxBatchVO(Long id, String batchNo) {
}
