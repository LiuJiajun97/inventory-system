package com.company.inventory.model.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出入库流水表实体(表 StockTransaction)。
 *
 * <p>流水只插不改,必带 afterQty(事务内扣减/增加后回读)。</p>
 *
 * @author inventory
 */
@TableName("\"StockTransaction\"")
public class StockTransactionDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 仓库 ID。 */
    @TableField("\"warehouseId\"")
    private Long warehouseId;
    /** 物品 ID。 */
    @TableField("\"itemId\"")
    private Long itemId;
    /** 批次 ID,无批次为 0。 */
    @TableField("\"batchId\"")
    private Long batchId;
    /** 库位 ID,无库位为 0。 */
    @TableField("\"locationId\"")
    private Long locationId;
    /** 变动数量(入库为正,出库为负)。 */
    @TableField("\"changeQty\"")
    private BigDecimal changeQty;
    /** 变动后余额。 */
    @TableField("\"afterQty\"")
    private BigDecimal afterQty;
    /** 业务类型:inbound / outbound。 */
    @TableField("\"bizCode\"")
    private String bizCode;
    /** 关联单据号。 */
    @TableField("\"docNo\"")
    private String docNo;
    /** 操作人。 */
    @TableField("\"operator\"")
    private String operator;
    /** 创建时间(UTC)。 */
    @TableField("\"createdAt\"")
    private LocalDateTime createdAt;
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
     * 获取仓库 ID。
     *
     * @return 仓库 ID
     */
    public Long getWarehouseId() {
        return warehouseId;
    }

    /**
     * 设置仓库 ID。
     *
     * @param warehouseId 仓库 ID
     */
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    /**
     * 获取物品 ID。
     *
     * @return 物品 ID
     */
    public Long getItemId() {
        return itemId;
    }

    /**
     * 设置物品 ID。
     *
     * @param itemId 物品 ID
     */
    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    /**
     * 获取批次 ID。
     *
     * @return 批次 ID
     */
    public Long getBatchId() {
        return batchId;
    }

    /**
     * 设置批次 ID。
     *
     * @param batchId 批次 ID
     */
    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    /**
     * 获取库位 ID。
     *
     * @return 库位 ID
     */
    public Long getLocationId() {
        return locationId;
    }

    /**
     * 设置库位 ID。
     *
     * @param locationId 库位 ID
     */
    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    /**
     * 获取变动数量。
     *
     * @return 变动数量
     */
    public BigDecimal getChangeQty() {
        return changeQty;
    }

    /**
     * 设置变动数量。
     *
     * @param changeQty 变动数量
     */
    public void setChangeQty(BigDecimal changeQty) {
        this.changeQty = changeQty;
    }

    /**
     * 获取变动后余额。
     *
     * @return 变动后余额
     */
    public BigDecimal getAfterQty() {
        return afterQty;
    }

    /**
     * 设置变动后余额。
     *
     * @param afterQty 变动后余额
     */
    public void setAfterQty(BigDecimal afterQty) {
        this.afterQty = afterQty;
    }

    /**
     * 获取业务类型。
     *
     * @return 业务类型
     */
    public String getBizCode() {
        return bizCode;
    }

    /**
     * 设置业务类型。
     *
     * @param bizCode 业务类型
     */
    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    /**
     * 获取单据号。
     *
     * @return 单据号
     */
    public String getDocNo() {
        return docNo;
    }

    /**
     * 设置单据号。
     *
     * @param docNo 单据号
     */
    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    /**
     * 获取操作人。
     *
     * @return 操作人
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 设置操作人。
     *
     * @param operator 操作人
     */
    public void setOperator(String operator) {
        this.operator = operator;
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
}
