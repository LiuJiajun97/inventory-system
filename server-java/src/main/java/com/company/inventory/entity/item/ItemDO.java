package com.company.inventory.entity.item;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 物品表实体(表 Item)。
 *
 * @author inventory
 */
@TableName("\"Item\"")
public class ItemDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 物品编码(唯一)。 */
    @TableField("\"itemCode\"")
    private String itemCode;
    /** 物品名称。 */
    @TableField("\"itemName\"")
    private String itemName;
    /** 单位。 */
    @TableField("\"unit\"")
    private String unit;
    /** 规格。 */
    @TableField("\"spec\"")
    private String spec;
    /** 扩展属性(JSON 字符串)。 */
    @TableField("\"attributes\"")
    private String attributes;
    /** 物料分类(轻量单级,可空)。 */
    @TableField("\"category\"")
    private String category;
    /** 最低库存预警线(可空,非空才参与低库存预警)。 */
    @TableField("\"minStock\"")
    private java.math.BigDecimal minStock;
    /** 默认税率(百分数,默认 13.00,单据行取默认可改)。 */
    @TableField("\"defaultTaxRate\"")
    private java.math.BigDecimal defaultTaxRate;
    /** 状态:1 启用。 */
    @TableField("\"status\"")
    private Integer status;
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
     * 获取物品编码。
     *
     * @return 物品编码
     */
    public String getItemCode() {
        return itemCode;
    }

    /**
     * 设置物品编码。
     *
     * @param itemCode 物品编码
     */
    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    /**
     * 获取物品名称。
     *
     * @return 物品名称
     */
    public String getItemName() {
        return itemName;
    }

    /**
     * 设置物品名称。
     *
     * @param itemName 物品名称
     */
    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    /**
     * 获取单位。
     *
     * @return 单位
     */
    public String getUnit() {
        return unit;
    }

    /**
     * 设置单位。
     *
     * @param unit 单位
     */
    public void setUnit(String unit) {
        this.unit = unit;
    }

    /**
     * 获取规格。
     *
     * @return 规格
     */
    public String getSpec() {
        return spec;
    }

    /**
     * 设置规格。
     *
     * @param spec 规格
     */
    public void setSpec(String spec) {
        this.spec = spec;
    }

    /**
     * 获取扩展属性。
     *
     * @return 扩展属性 JSON 字符串
     */
    public String getAttributes() {
        return attributes;
    }

    /**
     * 设置扩展属性。
     *
     * @param attributes 扩展属性 JSON 字符串
     */
    public void setAttributes(String attributes) {
        this.attributes = attributes;
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
     * 获取物料分类。
     *
     * @return 物料分类
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置物料分类。
     *
     * @param category 物料分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * 获取最低库存预警线。
     *
     * @return 最低库存预警线
     */
    public java.math.BigDecimal getMinStock() {
        return minStock;
    }

    /**
     * 设置最低库存预警线。
     *
     * @param minStock 最低库存预警线
     */
    public void setMinStock(java.math.BigDecimal minStock) {
        this.minStock = minStock;
    }

    /**
     * 获取默认税率。
     *
     * @return 默认税率
     */
    public java.math.BigDecimal getDefaultTaxRate() {
        return defaultTaxRate;
    }

    /**
     * 设置默认税率。
     *
     * @param defaultTaxRate 默认税率
     */
    public void setDefaultTaxRate(java.math.BigDecimal defaultTaxRate) {
        this.defaultTaxRate = defaultTaxRate;
    }
}
