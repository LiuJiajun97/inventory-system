package com.company.inventory.entity.warehouse;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 仓库表实体(表 Warehouse),4 个 enable 开关注驱动出入库必填校验。
 *
 * @author inventory
 */
@TableName("\"Warehouse\"")
public class WarehouseDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 仓库编码(唯一)。 */
    @TableField("\"warehouseCode\"")
    private String warehouseCode;
    /** 仓库名称。 */
    @TableField("\"warehouseName\"")
    private String warehouseName;
    /** 仓库类型:raw / finished / hardware 等。 */
    @TableField("\"warehouseType\"")
    private String warehouseType;
    /** 启用批次。 */
    @TableField("\"enableBatch\"")
    private Boolean enableBatch;
    /** 启用保质期(要求同时启用批次)。 */
    @TableField("\"enableExpiry\"")
    private Boolean enableExpiry;
    /** 启用序列号。 */
    @TableField("\"enableSerial\"")
    private Boolean enableSerial;
    /** 启用库位。 */
    @TableField("\"enableLocation\"")
    private Boolean enableLocation;
    /** 状态:1 启用。 */
    @TableField("\"status\"")
    private Integer status;
    /** 创建人。 */
    @TableField(value = "\"creator\"", fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间(UTC)。 */
    @TableField(value = "\"createdAt\"", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(value = "\"updater\"", fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(value = "\"updatedAt\"", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键。
     *
     * @param id 主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取仓库编码。
     *
     * @return 仓库编码
     */
    public String getWarehouseCode() {
        return warehouseCode;
    }

    /**
     * 设置仓库编码。
     *
     * @param warehouseCode 仓库编码
     */
    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    /**
     * 获取仓库名称。
     *
     * @return 仓库名称
     */
    public String getWarehouseName() {
        return warehouseName;
    }

    /**
     * 设置仓库名称。
     *
     * @param warehouseName 仓库名称
     */
    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    /**
     * 获取仓库类型。
     *
     * @return 仓库类型
     */
    public String getWarehouseType() {
        return warehouseType;
    }

    /**
     * 设置仓库类型。
     *
     * @param warehouseType 仓库类型
     */
    public void setWarehouseType(String warehouseType) {
        this.warehouseType = warehouseType;
    }

    /**
     * 是否启用批次。
     *
     * @return true 表示启用
     */
    public Boolean getEnableBatch() {
        return enableBatch;
    }

    /**
     * 设置批次开关。
     *
     * @param enableBatch 开关
     */
    public void setEnableBatch(Boolean enableBatch) {
        this.enableBatch = enableBatch;
    }

    /**
     * 是否启用保质期。
     *
     * @return true 表示启用
     */
    public Boolean getEnableExpiry() {
        return enableExpiry;
    }

    /**
     * 设置保质期开关。
     *
     * @param enableExpiry 开关
     */
    public void setEnableExpiry(Boolean enableExpiry) {
        this.enableExpiry = enableExpiry;
    }

    /**
     * 是否启用序列号。
     *
     * @return true 表示启用
     */
    public Boolean getEnableSerial() {
        return enableSerial;
    }

    /**
     * 设置序列号开关。
     *
     * @param enableSerial 开关
     */
    public void setEnableSerial(Boolean enableSerial) {
        this.enableSerial = enableSerial;
    }

    /**
     * 是否启用库位。
     *
     * @return true 表示启用
     */
    public Boolean getEnableLocation() {
        return enableLocation;
    }

    /**
     * 设置库位开关。
     *
     * @param enableLocation 开关
     */
    public void setEnableLocation(Boolean enableLocation) {
        this.enableLocation = enableLocation;
    }

    /**
     * 获取状态。
     *
     * @return 状态
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取创建人。
     *
     * @return 创建人
     */
    public String getCreator() {
        return creator;
    }

    /**
     * 设置创建人。
     *
     * @param creator 创建人
     */
    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * 获取修改人。
     *
     * @return 修改人
     */
    public String getUpdater() {
        return updater;
    }

    /**
     * 设置修改人。
     *
     * @param updater 修改人
     */
    public void setUpdater(String updater) {
        this.updater = updater;
    }

    /**
     * 获取修改时间。
     *
     * @return 修改时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置修改时间。
     *
     * @param updatedAt 修改时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
