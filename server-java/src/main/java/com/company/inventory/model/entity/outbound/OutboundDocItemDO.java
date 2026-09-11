package com.company.inventory.model.entity.outbound;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 出库单行表实体(表 OutboundDocItem)。
 *
 * @author inventory
 */
@TableName("\"OutboundDocItem\"")
public class OutboundDocItemDO {

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
    /** 出库参考单价(销售发货携带订单行单价,可空)。 */
    @TableField("\"unitPrice\"")
    private BigDecimal unitPrice;
    /** 关联销售订单行 ID(可空)。 */
    @TableField("\"refLineId\"")
    private Long refLineId;

    /**
     * 获取出库参考单价(销售发货携带订单行单价,可空)。
     *
     * @return 出库参考单价(销售发货携带订单行单价,可空)
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 设置出库参考单价(销售发货携带订单行单价,可空)。
     *
     * @param unitPrice 出库参考单价(销售发货携带订单行单价,可空)
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * 获取关联销售订单行 ID(可空)。
     *
     * @return 关联销售订单行 ID(可空)
     */
    public Long getRefLineId() {
        return refLineId;
    }

    /**
     * 设置关联销售订单行 ID(可空)。
     *
     * @param refLineId 关联销售订单行 ID(可空)
     */
    public void setRefLineId(Long refLineId) {
        this.refLineId = refLineId;
    }
}
