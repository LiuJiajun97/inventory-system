package com.company.inventory.model.vo.location;
/**
 * 库位出参(契约:4 字段)。
 *
 * @param id           主键
 * @param warehouseId  仓库 ID
 * @param locationCode 库位编码
 * @param locationName 库位名称
 * @author inventory
 */
public record LocationVO(Long id, Long warehouseId, String locationCode, String locationName) {
}
