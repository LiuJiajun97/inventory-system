package com.company.inventory.model.entity.inbound;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 入库单行表实体(表 InboundDocItem)。
 *
 * @author inventory
 */
@TableName("\"InboundDocItem\"")
public class InboundDocItemDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 单据 ID。 */
    @TableField("\"docId\"")
    private Long docId;
    /** 物品 ID。 */
    @TableField("\"itemId\"")
    private Long itemId;
    /** 数量。 */
    @TableField("\"quantity\"")
    private BigDecimal quantity;
    /** 批次 ID,无批次为 0。 */
    @TableField("\"batchId\"")
    private Long batchId;
    /** 库位 ID,无库位为 0。 */
    @TableField("\"locationId\"")
    private Long locationId;
    /** 序列号列表(JSON 字符串)。 */
    @TableField("\"serialNos\"")
    private String serialNos;
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
     * 获取单据 ID。
     *
     * @return 单据 ID
     */
    public Long getDocId() {
        return docId;
    }

    /**
     * 设置单据 ID。
     *
     * @param docId 单据 ID
     */
    public void setDocId(Long docId) {
        this.docId = docId;
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
     * 获取数量。
     *
     * @return 数量
     */
    public BigDecimal getQuantity() {
        return quantity;
    }

    /**
     * 设置数量。
     *
     * @param quantity 数量
     */
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
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
     * 获取序列号 JSON 字符串。
     *
     * @return 序列号 JSON 字符串
     */
    public String getSerialNos() {
        return serialNos;
    }

    /**
     * 设置序列号 JSON 字符串。
     *
     * @param serialNos 序列号 JSON 字符串
     */
    public void setSerialNos(String serialNos) {
        this.serialNos = serialNos;
    }
    /** 入库不含税单价(采购到货携带订单行单价,可空)。 */
    @TableField("\"unitPrice\"")
    private BigDecimal unitPrice;
    /** 税率(百分数,可空)。 */
    @TableField("\"taxRate\"")
    private BigDecimal taxRate;
    /** 批次号(新批次或已有批次,可空)。 */
    @TableField("\"batchNo\"")
    private String batchNo;
    /** 生产日期(建批次用,可空)。 */
    @TableField("\"productionDate\"")
    private java.time.LocalDate productionDate;
    /** 到期日期(建批次用,可空)。 */
    @TableField("\"expiryDate\"")
    private java.time.LocalDate expiryDate;
    /** 关联采购订单行 ID(可空)。 */
    @TableField("\"refLineId\"")
    private Long refLineId;

    /**
     * 获取入库不含税单价(采购到货携带订单行单价,可空)。
     *
     * @return 入库不含税单价(采购到货携带订单行单价,可空)
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 设置入库不含税单价(采购到货携带订单行单价,可空)。
     *
     * @param unitPrice 入库不含税单价(采购到货携带订单行单价,可空)
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * 获取税率(百分数,可空)。
     *
     * @return 税率(百分数,可空)
     */
    public BigDecimal getTaxRate() {
        return taxRate;
    }

    /**
     * 设置税率(百分数,可空)。
     *
     * @param taxRate 税率(百分数,可空)
     */
    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    /**
     * 获取批次号(新批次或已有批次,可空)。
     *
     * @return 批次号(新批次或已有批次,可空)
     */
    public String getBatchNo() {
        return batchNo;
    }

    /**
     * 设置批次号(新批次或已有批次,可空)。
     *
     * @param batchNo 批次号(新批次或已有批次,可空)
     */
    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    /**
     * 获取生产日期(建批次用,可空)。
     *
     * @return 生产日期(建批次用,可空)
     */
    public java.time.LocalDate getProductionDate() {
        return productionDate;
    }

    /**
     * 设置生产日期(建批次用,可空)。
     *
     * @param productionDate 生产日期(建批次用,可空)
     */
    public void setProductionDate(java.time.LocalDate productionDate) {
        this.productionDate = productionDate;
    }

    /**
     * 获取到期日期(建批次用,可空)。
     *
     * @return 到期日期(建批次用,可空)
     */
    public java.time.LocalDate getExpiryDate() {
        return expiryDate;
    }

    /**
     * 设置到期日期(建批次用,可空)。
     *
     * @param expiryDate 到期日期(建批次用,可空)
     */
    public void setExpiryDate(java.time.LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * 获取关联采购订单行 ID(可空)。
     *
     * @return 关联采购订单行 ID(可空)
     */
    public Long getRefLineId() {
        return refLineId;
    }

    /**
     * 设置关联采购订单行 ID(可空)。
     *
     * @param refLineId 关联采购订单行 ID(可空)
     */
    public void setRefLineId(Long refLineId) {
        this.refLineId = refLineId;
    }
}
