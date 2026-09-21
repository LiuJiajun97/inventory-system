package com.company.inventory.model.vo.stock;

import java.time.LocalDateTime;

/**
 * 流水行出参(契约:changeQty/afterQty 为字符串;warehouse/item/batch 为精简内嵌对象)。
 *
 * @param id          主键
 * @param warehouseId 仓库 ID
 * @param itemId      物品 ID
 * @param batchId     批次 ID
 * @param locationId  库位 ID
 * @param changeQty   变动数量(字符串)
 * @param afterQty    变动后余额(字符串)
 * @param bizCode     业务类型
 * @param docNo       单据号
 * @param operator    操作人
 * @param createdAt   创建时间
 * @param warehouse   仓库精简对象
 * @param item        物品精简对象
 * @param batch       批次精简对象(无批次为 null)
 * @author inventory
 */
public record TransactionVO(Long id, Long warehouseId, Long itemId, Long batchId, Long locationId,
        String changeQty, String afterQty, String bizCode, String docNo, String operator,
        LocalDateTime createdAt, TxWarehouseVO warehouse, TxItemVO item, TxBatchVO batch) {
}
