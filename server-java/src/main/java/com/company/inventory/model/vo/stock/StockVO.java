package com.company.inventory.model.vo.stock;

import com.company.inventory.model.vo.item.ItemVO;
import com.company.inventory.model.vo.location.LocationVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;
/**
 * 库存行出参(契约:quantity 为字符串,warehouse/item/batch/location 内嵌对象可空)。
 *
 * @param id          主键
 * @param warehouseId 仓库 ID
 * @param itemId      物品 ID
 * @param batchId     批次 ID
 * @param locationId  库位 ID
 * @param quantity    库存数量(字符串,契约要求)
 * @param preAllocatedQty 预占量(字符串,契约增量字段)
 * @param availableQty 可用数量=quantity-preAllocatedQty(字符串,契约增量字段)
 * @param warehouse   仓库对象
 * @param item        物品对象
 * @param batch       批次对象(无批次为 null)
 * @param location    库位对象(无库位为 null)
 * @author inventory
 */
public record StockVO(Long id, Long warehouseId, Long itemId, Long batchId, Long locationId,
        String quantity, String preAllocatedQty, String availableQty,
        WarehouseVO warehouse, ItemVO item, BatchVO batch, LocationVO location) {
}
