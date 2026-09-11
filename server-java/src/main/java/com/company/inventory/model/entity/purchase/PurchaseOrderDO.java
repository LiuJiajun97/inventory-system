package com.company.inventory.model.entity.purchase;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单表头实体(表 PurchaseOrder,状态机 draft/pending/approved/completed/closed/rejected/voided)。
 *
 * @author inventory
 */
@TableName("\"PurchaseOrder\"")
public class PurchaseOrderDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 单据编号。 */
    @TableField("\"docNo\"")
    private String docNo;
    /** 单据日期。 */
    @TableField("\"docDate\"")
    private LocalDate docDate;
    /** 供应商 ID。 */
    @TableField("\"supplierId\"")
    private Long supplierId;
    /** 采购员用户 ID。 */
    @TableField("\"buyerId\"")
    private Long buyerId;
    /** 超收比例。 */
    @TableField("\"allowOverReceiptRate\"")
    private BigDecimal allowOverReceiptRate;
    /** 整单不含税合计。 */
    @TableField("\"totalAmount\"")
    private BigDecimal totalAmount;
    /** 整单税额合计。 */
    @TableField("\"totalTaxAmount\"")
    private BigDecimal totalTaxAmount;
    /** 整单价税合计。 */
    @TableField("\"totalTaxInclusive\"")
    private BigDecimal totalTaxInclusive;
    /** 单据状态。 */
    @TableField("\"status\"")
    private String status;
    /** 制单人。 */
    @TableField(value = "\"creator\"", fill = FieldFill.INSERT)
    private String creator;
    /** 制单时间。 */
    @TableField(value = "\"createdAt\"", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(value = "\"updater\"", fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(value = "\"updatedAt\"", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 审批人。 */
    @TableField("\"approver\"")
    private String approver;
    /** 审批时间。 */
    @TableField("\"approvedAt\"")
    private LocalDateTime approvedAt;
    /** 驳回原因。 */
    @TableField("\"rejectReason\"")
    private String rejectReason;
    /** 备注。 */
    @TableField("\"remark\"")
    private String remark;

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
     * 获取单据编号。
     *
     * @return 单据编号
     */
    public String getDocNo() {
        return docNo;
    }

    /**
     * 设置单据编号。
     *
     * @param docNo 单据编号
     */
    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    /**
     * 获取单据日期。
     *
     * @return 单据日期
     */
    public LocalDate getDocDate() {
        return docDate;
    }

    /**
     * 设置单据日期。
     *
     * @param docDate 单据日期
     */
    public void setDocDate(LocalDate docDate) {
        this.docDate = docDate;
    }

    /**
     * 获取供应商 ID。
     *
     * @return 供应商 ID
     */
    public Long getSupplierId() {
        return supplierId;
    }

    /**
     * 设置供应商 ID。
     *
     * @param supplierId 供应商 ID
     */
    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    /**
     * 获取采购员用户 ID。
     *
     * @return 采购员用户 ID
     */
    public Long getBuyerId() {
        return buyerId;
    }

    /**
     * 设置采购员用户 ID。
     *
     * @param buyerId 采购员用户 ID
     */
    public void setBuyerId(Long buyerId) {
        this.buyerId = buyerId;
    }

    /**
     * 获取超收比例。
     *
     * @return 超收比例
     */
    public BigDecimal getAllowOverReceiptRate() {
        return allowOverReceiptRate;
    }

    /**
     * 设置超收比例。
     *
     * @param allowOverReceiptRate 超收比例
     */
    public void setAllowOverReceiptRate(BigDecimal allowOverReceiptRate) {
        this.allowOverReceiptRate = allowOverReceiptRate;
    }

    /**
     * 获取整单不含税合计。
     *
     * @return 整单不含税合计
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * 设置整单不含税合计。
     *
     * @param totalAmount 整单不含税合计
     */
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    /**
     * 获取整单税额合计。
     *
     * @return 整单税额合计
     */
    public BigDecimal getTotalTaxAmount() {
        return totalTaxAmount;
    }

    /**
     * 设置整单税额合计。
     *
     * @param totalTaxAmount 整单税额合计
     */
    public void setTotalTaxAmount(BigDecimal totalTaxAmount) {
        this.totalTaxAmount = totalTaxAmount;
    }

    /**
     * 获取整单价税合计。
     *
     * @return 整单价税合计
     */
    public BigDecimal getTotalTaxInclusive() {
        return totalTaxInclusive;
    }

    /**
     * 设置整单价税合计。
     *
     * @param totalTaxInclusive 整单价税合计
     */
    public void setTotalTaxInclusive(BigDecimal totalTaxInclusive) {
        this.totalTaxInclusive = totalTaxInclusive;
    }

    /**
     * 获取单据状态。
     *
     * @return 单据状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置单据状态。
     *
     * @param status 单据状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取制单人。
     *
     * @return 制单人
     */
    public String getCreator() {
        return creator;
    }

    /**
     * 设置制单人。
     *
     * @param creator 制单人
     */
    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * 获取制单时间。
     *
     * @return 制单时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置制单时间。
     *
     * @param createdAt 制单时间
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

    /**
     * 获取审批人。
     *
     * @return 审批人
     */
    public String getApprover() {
        return approver;
    }

    /**
     * 设置审批人。
     *
     * @param approver 审批人
     */
    public void setApprover(String approver) {
        this.approver = approver;
    }

    /**
     * 获取审批时间。
     *
     * @return 审批时间
     */
    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    /**
     * 设置审批时间。
     *
     * @param approvedAt 审批时间
     */
    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    /**
     * 获取驳回原因。
     *
     * @return 驳回原因
     */
    public String getRejectReason() {
        return rejectReason;
    }

    /**
     * 设置驳回原因。
     *
     * @param rejectReason 驳回原因
     */
    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
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
}
