package com.company.inventory.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 批次表实体(表 Batch)。
 *
 * <p>唯一键 (itemId, batchNo);生产日期/保质期为 DATE 类型。</p>
 *
 * @author inventory
 */
@TableName("\"Batch\"")
public class BatchDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 物品 ID。 */
    @TableField("\"itemId\"")
    private Long itemId;
    /** 批次号。 */
    @TableField("\"batchNo\"")
    private String batchNo;
    /** 生产日期。 */
    @TableField("\"productionDate\"")
    private LocalDate productionDate;
    /** 保质期到期日。 */
    @TableField("\"expiryDate\"")
    private LocalDate expiryDate;
    /** 供应商。 */
    @TableField("\"supplier\"")
    private String supplier;
    /** 批次状态:active。 */
    @TableField("\"status\"")
    private String status;
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
     * 获取批次号。
     *
     * @return 批次号
     */
    public String getBatchNo() {
        return batchNo;
    }

    /**
     * 设置批次号。
     *
     * @param batchNo 批次号
     */
    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    /**
     * 获取生产日期。
     *
     * @return 生产日期
     */
    public LocalDate getProductionDate() {
        return productionDate;
    }

    /**
     * 设置生产日期。
     *
     * @param productionDate 生产日期
     */
    public void setProductionDate(LocalDate productionDate) {
        this.productionDate = productionDate;
    }

    /**
     * 获取保质期到期日。
     *
     * @return 到期日
     */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    /**
     * 设置保质期到期日。
     *
     * @param expiryDate 到期日
     */
    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * 获取供应商。
     *
     * @return 供应商
     */
    public String getSupplier() {
        return supplier;
    }

    /**
     * 设置供应商。
     *
     * @param supplier 供应商
     */
    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    /**
     * 获取批次状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置批次状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
