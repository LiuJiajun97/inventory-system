package com.company.inventory.entity.adjust;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/**
 * 库存调整单行实体(表 StockAdjustDocItem,qty 为绝对值,方向由 adjustType 决定)。
 *
 * @author inventory
 */
@TableName("\"StockAdjustDocItem\"")
public class StockAdjustDocItemDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 调整单 ID。 */
    @TableField("\"docId\"")
    private Long docId;
    /** 行号。 */
    @TableField("\"lineNo\"")
    private Integer lineNo;
    /** 物品 ID。 */
    @TableField("\"itemId\"")
    private Long itemId;
    /** 规格快照。 */
    @TableField("\"specSnapshot\"")
    private String specSnapshot;
    /** 单位快照。 */
    @TableField("\"unit\"")
    private String unit;
    /** 批次 ID。 */
    @TableField("\"batchId\"")
    private Long batchId;
    /** 库位 ID。 */
    @TableField("\"locationId\"")
    private Long locationId;
    /** 调整数量。 */
    @TableField("\"qty\"")
    private BigDecimal qty;
    /** 成本参考价。 */
    @TableField("\"unitPrice\"")
    private BigDecimal unitPrice;
    /** 原因。 */
    @TableField("\"reason\"")
    private String reason;
    /** 创建人。 */
    @TableField(value = "\"creator\"", fill = FieldFill.INSERT)
    private String creator;

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
     * 获取调整单 ID。
     *
     * @return 调整单 ID
     */
    public Long getDocId() {
        return docId;
    }

    /**
     * 设置调整单 ID。
     *
     * @param docId 调整单 ID
     */
    public void setDocId(Long docId) {
        this.docId = docId;
    }

    /**
     * 获取行号。
     *
     * @return 行号
     */
    public Integer getLineNo() {
        return lineNo;
    }

    /**
     * 设置行号。
     *
     * @param lineNo 行号
     */
    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
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
     * 获取规格快照。
     *
     * @return 规格快照
     */
    public String getSpecSnapshot() {
        return specSnapshot;
    }

    /**
     * 设置规格快照。
     *
     * @param specSnapshot 规格快照
     */
    public void setSpecSnapshot(String specSnapshot) {
        this.specSnapshot = specSnapshot;
    }

    /**
     * 获取单位快照。
     *
     * @return 单位快照
     */
    public String getUnit() {
        return unit;
    }

    /**
     * 设置单位快照。
     *
     * @param unit 单位快照
     */
    public void setUnit(String unit) {
        this.unit = unit;
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
     * 获取调整数量。
     *
     * @return 调整数量
     */
    public BigDecimal getQty() {
        return qty;
    }

    /**
     * 设置调整数量。
     *
     * @param qty 调整数量
     */
    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    /**
     * 获取成本参考价。
     *
     * @return 成本参考价
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 设置成本参考价。
     *
     * @param unitPrice 成本参考价
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * 获取原因。
     *
     * @return 原因
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置原因。
     *
     * @param reason 原因
     */
    public void setReason(String reason) {
        this.reason = reason;
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
}
