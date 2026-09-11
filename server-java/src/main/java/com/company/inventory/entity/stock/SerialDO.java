package com.company.inventory.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 序列号表实体(表 Serial)。
 *
 * <p>唯一键 (itemId, serialNo);状态 in_stock / out。</p>
 *
 * @author inventory
 */
@TableName("\"Serial\"")
public class SerialDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 物品 ID。 */
    @TableField("\"itemId\"")
    private Long itemId;
    /** 序列号。 */
    @TableField("\"serialNo\"")
    private String serialNo;
    /** 所在仓库 ID(可为空)。 */
    @TableField("\"warehouseId\"")
    private Long warehouseId;
    /** 状态:in_stock / out。 */
    @TableField("\"status\"")
    private String status;
    /** 入库时间(UTC)。 */
    @TableField("\"inboundTime\"")
    private LocalDateTime inboundTime;
    /** 出库时间(UTC)。 */
    @TableField("\"outboundTime\"")
    private LocalDateTime outboundTime;
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
     * 获取序列号。
     *
     * @return 序列号
     */
    public String getSerialNo() {
        return serialNo;
    }

    /**
     * 设置序列号。
     *
     * @param serialNo 序列号
     */
    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
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
     * 获取状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取入库时间。
     *
     * @return 入库时间
     */
    public LocalDateTime getInboundTime() {
        return inboundTime;
    }

    /**
     * 设置入库时间。
     *
     * @param inboundTime 入库时间
     */
    public void setInboundTime(LocalDateTime inboundTime) {
        this.inboundTime = inboundTime;
    }

    /**
     * 获取出库时间。
     *
     * @return 出库时间
     */
    public LocalDateTime getOutboundTime() {
        return outboundTime;
    }

    /**
     * 设置出库时间。
     *
     * @param outboundTime 出库时间
     */
    public void setOutboundTime(LocalDateTime outboundTime) {
        this.outboundTime = outboundTime;
    }
}
