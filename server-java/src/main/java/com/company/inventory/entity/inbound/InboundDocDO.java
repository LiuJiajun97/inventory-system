package com.company.inventory.entity.inbound;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 入库单表实体(表 InboundDoc)。
 *
 * @author inventory
 */
@TableName("\"InboundDoc\"")
public class InboundDocDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 单据号(RK-YYYYMMDD-NNNN,唯一)。 */
    @TableField("\"docNo\"")
    private String docNo;
    /** 仓库 ID。 */
    @TableField("\"warehouseId\"")
    private Long warehouseId;
    /** 单据状态:finished。 */
    @TableField("\"status\"")
    private String status;
    /** 备注。 */
    @TableField("\"remark\"")
    private String remark;
    /** 创建人。 */
    @TableField("\"creator\"")
    private String creator;
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
     * 获取单据状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置单据状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取备注。
     *
     * @return 备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置备注。
     *
     * @param remark 备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
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
    /** 关联单据类型(purchase=采购到货,空=手工入库)。 */
    @TableField("\"refType\"")
    private String refType;
    /** 关联采购订单 ID(可空)。 */
    @TableField("\"refDocId\"")
    private Long refDocId;
    /** 单据日期(可空)。 */
    @TableField("\"docDate\"")
    private java.time.LocalDate docDate;

    /**
     * 获取关联单据类型(purchase=采购到货,空=手工入库)。
     *
     * @return 关联单据类型(purchase=采购到货,空=手工入库)
     */
    public String getRefType() {
        return refType;
    }

    /**
     * 设置关联单据类型(purchase=采购到货,空=手工入库)。
     *
     * @param refType 关联单据类型(purchase=采购到货,空=手工入库)
     */
    public void setRefType(String refType) {
        this.refType = refType;
    }

    /**
     * 获取关联采购订单 ID(可空)。
     *
     * @return 关联采购订单 ID(可空)
     */
    public Long getRefDocId() {
        return refDocId;
    }

    /**
     * 设置关联采购订单 ID(可空)。
     *
     * @param refDocId 关联采购订单 ID(可空)
     */
    public void setRefDocId(Long refDocId) {
        this.refDocId = refDocId;
    }

    /**
     * 获取单据日期(可空)。
     *
     * @return 单据日期(可空)
     */
    public java.time.LocalDate getDocDate() {
        return docDate;
    }

    /**
     * 设置单据日期(可空)。
     *
     * @param docDate 单据日期(可空)
     */
    public void setDocDate(java.time.LocalDate docDate) {
        this.docDate = docDate;
    }
}
