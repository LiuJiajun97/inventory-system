package com.company.inventory.dto.location;

/**
 * 编辑库位入参(库位编码/所属仓库不在此列:被 Stock/StockTransaction/多张单据明细
 * 按 id 引用,锁死为不可改;LocationDO 自身无 status 字段,本次也不加停用)。
 *
 * <p>locationName 可空=不更新;为避免空 PUT 误触,调用方应保持至少一个字段非空,
 * 但本 DTO 仅 1 个字段,故省略"至少一个非空"校验。</p>
 *
 * @param locationName 库位名称
 * @author inventory
 */
public record LocationUpdateDTO(String locationName) {
}