package com.company.inventory.entity.transfer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/**
 * 调拨单行实体(表 TransferDocItem)。
 *
 * @author inventory
 */
@TableName("\"TransferDocItem\"")
public class TransferDocItemDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 调拨单 ID。 */
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
    /** 调拨数量。 */
    @TableField("\"qty\"")
    private BigDecimal qty;
    /** 成本参考单价。 */
    @TableField("\"unitPrice\"")
    private BigDecimal unitPrice;
    /** 源库位 ID。 */
    @TableField("\"fromLocationId\"")
    private Long fromLocationId;
    /** 目的库位 ID。 */
    @TableField("\"toLocationId\"")
    private Long toLocationId;
    /** 行备注。 */
    @TableField("\"lineRemark\"")
    private String lineRemark;

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
     * 获取调拨单 ID。
     *
     * @return 调拨单 ID
     */
    public Long getDocId() {
        return docId;
    }

    /**
     * 设置调拨单 ID。
     *
     * @param docId 调拨单 ID
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
     * 获取调拨数量。
     *
     * @return 调拨数量
     */
    public BigDecimal getQty() {
        return qty;
    }

    /**
     * 设置调拨数量。
     *
     * @param qty 调拨数量
     */
    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    /**
     * 获取成本参考单价。
     *
     * @return 成本参考单价
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 设置成本参考单价。
     *
     * @param unitPrice 成本参考单价
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * 获取源库位 ID。
     *
     * @return 源库位 ID
     */
    public Long getFromLocationId() {
        return fromLocationId;
    }

    /**
     * 设置源库位 ID。
     *
     * @param fromLocationId 源库位 ID
     */
    public void setFromLocationId(Long fromLocationId) {
        this.fromLocationId = fromLocationId;
    }

    /**
     * 获取目的库位 ID。
     *
     * @return 目的库位 ID
     */
    public Long getToLocationId() {
        return toLocationId;
    }

    /**
     * 设置目的库位 ID。
     *
     * @param toLocationId 目的库位 ID
     */
    public void setToLocationId(Long toLocationId) {
        this.toLocationId = toLocationId;
    }

    /**
     * 获取行备注。
     *
     * @return 行备注
     */
    public String getLineRemark() {
        return lineRemark;
    }

    /**
     * 设置行备注。
     *
     * @param lineRemark 行备注
     */
    public void setLineRemark(String lineRemark) {
        this.lineRemark = lineRemark;
    }
}
