package com.company.inventory.model.entity.outbound;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 出库单表实体(表 OutboundDoc)。
 *
 * @author inventory
 */
@TableName("\"OutboundDoc\"")
public class OutboundDocDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 单据号(CK-YYYYMMDD-NNNN,唯一)。 */
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
    @TableField(value = "\"creator\"", fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间(UTC)。 */
    @TableField(value = "\"createdAt\"", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(value = "\"updater\"", fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(value = "\"updatedAt\"", fill = FieldFill.INSERT_UPDATE)
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

    /**
     * 获取修改人。
     *
     * @return 修改人
     */
    public String getUpdater() {
        return updater;
    }

    /**
     * 设置修改人。
     *
     * @param updater 修改人
     */
    public void setUpdater(String updater) {
        this.updater = updater;
    }

    /**
     * 获取修改时间。
     *
     * @return 修改时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置修改时间。
     *
     * @param updatedAt 修改时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    /** 关联单据类型(sales=销售发货,空=手工出库)。 */
    @TableField("\"refType\"")
    private String refType;
    /** 关联销售订单 ID(可空)。 */
    @TableField("\"refDocId\"")
    private Long refDocId;
    /** 单据日期(可空)。 */
    @TableField("\"docDate\"")
    private java.time.LocalDate docDate;

    /**
     * 获取关联单据类型(sales=销售发货,空=手工出库)。
     *
     * @return 关联单据类型(sales=销售发货,空=手工出库)
     */
    public String getRefType() {
        return refType;
    }

    /**
     * 设置关联单据类型(sales=销售发货,空=手工出库)。
     *
     * @param refType 关联单据类型(sales=销售发货,空=手工出库)
     */
    public void setRefType(String refType) {
        this.refType = refType;
    }

    /**
     * 获取关联销售订单 ID(可空)。
     *
     * @return 关联销售订单 ID(可空)
     */
    public Long getRefDocId() {
        return refDocId;
    }

    /**
     * 设置关联销售订单 ID(可空)。
     *
     * @param refDocId 关联销售订单 ID(可空)
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
