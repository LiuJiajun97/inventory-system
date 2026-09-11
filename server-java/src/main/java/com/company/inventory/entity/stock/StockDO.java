package com.company.inventory.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存余额表实体(表 Stock)。
 *
 * <p>唯一键 (warehouseId, itemId, batchId, locationId),无批次/库位时以 0 占位;
 * quantity 为 DECIMAL(18,4)。</p>
 *
 * @author inventory
 */
@TableName("\"Stock\"")
public class StockDO {

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
    /** 库存数量。 */
    @TableField("\"quantity\"")
    private BigDecimal quantity;
    /** 预占量(销售订单审批预占,方案 §5.2;可用=quantity-preAllocatedQty)。 */
    @TableField("\"preAllocatedQty\"")
    private BigDecimal preAllocatedQty;
    /** 更新时间(UTC)。 */
    @TableField("\"updatedAt\"")
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
     * 获取库存数量。
     *
     * @return 数量
     */
    public BigDecimal getQuantity() {
        return quantity;
    }

    /**
     * 设置库存数量。
     *
     * @param quantity 数量
     */
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    /**
     * 获取预占量。
     *
     * @return 预占量
     */
    public BigDecimal getPreAllocatedQty() {
        return preAllocatedQty;
    }

    /**
     * 设置预占量。
     *
     * @param preAllocatedQty 预占量
     */
    public void setPreAllocatedQty(BigDecimal preAllocatedQty) {
        this.preAllocatedQty = preAllocatedQty;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
