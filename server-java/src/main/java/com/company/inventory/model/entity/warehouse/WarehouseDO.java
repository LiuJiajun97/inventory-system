package com.company.inventory.model.entity.warehouse;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 仓库表实体(表 Warehouse),4 个 enable 开关注驱动出入库必填校验。
 *
 * @author inventory
 */
@TableName("warehouse")
@Getter
@Setter
public class WarehouseDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 仓库编码(唯一)。 */
    private String warehouseCode;
    /** 仓库名称。 */
    private String warehouseName;
    /** 仓库类型:raw / finished / hardware 等。 */
    private String warehouseType;
    /** 启用批次。 */
    private Boolean enableBatch;
    /** 启用保质期(要求同时启用批次)。 */
    private Boolean enableExpiry;
    /** 启用序列号。 */
    private Boolean enableSerial;
    /** 启用库位。 */
    private Boolean enableLocation;
    /** 状态:1 启用。 */
    private Integer status;
    /** 默认仓(全表至多一个 true,可空)。 */
    private Boolean defaultWarehouse;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
