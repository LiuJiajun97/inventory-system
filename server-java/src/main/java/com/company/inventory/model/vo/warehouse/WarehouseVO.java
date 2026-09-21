package com.company.inventory.model.vo.warehouse;
import java.time.LocalDateTime;

/**
 * 仓库出参(契约:10 字段,裸对象/裸数组元素)。
 *
 * @param id             主键
 * @param warehouseCode  仓库编码
 * @param warehouseName  仓库名称
 * @param warehouseType  仓库类型
 * @param enableBatch    启用批次
 * @param enableExpiry   启用保质期
 * @param enableSerial   启用序列号
 * @param enableLocation 启用库位
 * @param status         状态
 * @param defaultWarehouse      默认仓(V10 增量)
 * @param createdAt      创建时间
 * @author inventory
 */
public record WarehouseVO(Long id, String warehouseCode, String warehouseName,
        String warehouseType, Boolean enableBatch, Boolean enableExpiry,
        Boolean enableSerial, Boolean enableLocation, Integer status, Boolean defaultWarehouse,
        LocalDateTime createdAt) {

    /** 兼容旧签名构造器(V10 新增 defaultWarehouse 默认 null,嵌套引用处不受影响)。 */
    public WarehouseVO(Long id, String warehouseCode, String warehouseName,
            String warehouseType, Boolean enableBatch, Boolean enableExpiry,
            Boolean enableSerial, Boolean enableLocation, Integer status,
            LocalDateTime createdAt) {
        this(id, warehouseCode, warehouseName, warehouseType, enableBatch, enableExpiry,
                enableSerial, enableLocation, status, null, createdAt);
    }
}
