package com.company.inventory.entity.purchase;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单行实体(表 PurchaseOrderItem)。
 *
 * @author inventory
 */
@TableName("\"PurchaseOrderItem\"")
public class PurchaseOrderItemDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 订单 ID。 */
    @TableField("\"orderId\"")
    private Long orderId;
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
    /** 订购数量。 */
    @TableField("\"orderedQty\"")
    private BigDecimal orderedQty;
    /** 累计到货数量。 */
    @TableField("\"arrivedQty\"")
    private BigDecimal arrivedQty;
    /** 计划交货日。 */
    @TableField("\"expectedDeliveryDate\"")
    private LocalDate expectedDeliveryDate;
    /** 不含税单价。 */
    @TableField("\"unitPrice\"")
    private BigDecimal unitPrice;
    /** 税率。 */
    @TableField("\"taxRate\"")
    private BigDecimal taxRate;
    /** 不含税金额。 */
    @TableField("\"amount\"")
    private BigDecimal amount;
    /** 税额。 */
    @TableField("\"taxAmount\"")
    private BigDecimal taxAmount;
    /** 价税合计。 */
    @TableField("\"taxInclusiveTotal\"")
    private BigDecimal taxInclusiveTotal;
    /** 行是否关闭。 */
    @TableField("\"closed\"")
    private Boolean closed;
    /** 行备注。 */
    @TableField("\"lineRemark\"")
    private String lineRemark;
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
     * 获取订单 ID。
     *
     * @return 订单 ID
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * 设置订单 ID。
     *
     * @param orderId 订单 ID
     */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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
     * 获取订购数量。
     *
     * @return 订购数量
     */
    public BigDecimal getOrderedQty() {
        return orderedQty;
    }

    /**
     * 设置订购数量。
     *
     * @param orderedQty 订购数量
     */
    public void setOrderedQty(BigDecimal orderedQty) {
        this.orderedQty = orderedQty;
    }

    /**
     * 获取累计到货数量。
     *
     * @return 累计到货数量
     */
    public BigDecimal getArrivedQty() {
        return arrivedQty;
    }

    /**
     * 设置累计到货数量。
     *
     * @param arrivedQty 累计到货数量
     */
    public void setArrivedQty(BigDecimal arrivedQty) {
        this.arrivedQty = arrivedQty;
    }

    /**
     * 获取计划交货日。
     *
     * @return 计划交货日
     */
    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    /**
     * 设置计划交货日。
     *
     * @param expectedDeliveryDate 计划交货日
     */
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) {
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    /**
     * 获取不含税单价。
     *
     * @return 不含税单价
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 设置不含税单价。
     *
     * @param unitPrice 不含税单价
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * 获取税率。
     *
     * @return 税率
     */
    public BigDecimal getTaxRate() {
        return taxRate;
    }

    /**
     * 设置税率。
     *
     * @param taxRate 税率
     */
    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    /**
     * 获取不含税金额。
     *
     * @return 不含税金额
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置不含税金额。
     *
     * @param amount 不含税金额
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 获取税额。
     *
     * @return 税额
     */
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    /**
     * 设置税额。
     *
     * @param taxAmount 税额
     */
    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    /**
     * 获取价税合计。
     *
     * @return 价税合计
     */
    public BigDecimal getTaxInclusiveTotal() {
        return taxInclusiveTotal;
    }

    /**
     * 设置价税合计。
     *
     * @param taxInclusiveTotal 价税合计
     */
    public void setTaxInclusiveTotal(BigDecimal taxInclusiveTotal) {
        this.taxInclusiveTotal = taxInclusiveTotal;
    }

    /**
     * 获取行是否关闭。
     *
     * @return 行是否关闭
     */
    public Boolean getClosed() {
        return closed;
    }

    /**
     * 设置行是否关闭。
     *
     * @param closed 行是否关闭
     */
    public void setClosed(Boolean closed) {
        this.closed = closed;
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
