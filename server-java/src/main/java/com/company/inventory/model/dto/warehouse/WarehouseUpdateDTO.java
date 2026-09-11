package com.company.inventory.model.dto.warehouse;

/**
 * 编辑仓库入参(编码不在此列:被 Stock/Inbound/Outbound/Transaction/Serial 等
 * 8+ 张业务表按 id 引用,锁死为不可改;其余字段可空=不更新,至少一个字段非空)。
 *
 * <p>字段可空即表示该字段不更新;至少一个非空字段才会真正下发到数据库,
 * 避免空 PUT 误改 createdAt 之类无关字段。</p>
 *
 * @param warehouseName  仓库名称
 * @param warehouseType  仓库类型
 * @param enableBatch    启用批次
 * @param enableExpiry   启用保质期
 * @param enableSerial   启用序列号
 * @param enableLocation 启用库位
 * @param status         状态,1 启用 0 停用
 * @author inventory
 */
public record WarehouseUpdateDTO(
        String warehouseName,
        String warehouseType,
        Boolean enableBatch,
        Boolean enableExpiry,
        Boolean enableSerial,
        Boolean enableLocation,
        Integer status) {
}