package com.company.inventory.model.entity.purchase;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 采购订单表头实体(表 PurchaseOrder,状态机 draft/pending/approved/completed/closed/rejected/voided)。
 *
 * @author inventory
 */
@TableName("purchase_order")
@Getter
@Setter
public class PurchaseOrderDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据编号。 */
    private String docNo;
    /** 单据日期。 */
    private LocalDate docDate;
    /** 供应商 ID。 */
    private Long supplierId;
    /** 采购员用户 ID。 */
    private Long buyerId;
    /** 超收比例。 */
    private BigDecimal allowOverReceiptRate;
    /** 整单不含税合计。 */
    private BigDecimal totalAmount;
    /** 整单税额合计。 */
    private BigDecimal totalTaxAmount;
    /** 整单价税合计。 */
    private BigDecimal totalTaxInclusive;
    /** 合同号(可空)。 */
    private String contractNo;
    /** 运费(可空)。 */
    private BigDecimal freight;
    /** 交货地址(可空)。 */
    private String shippingAddress;
    /** 折扣额(可空,不参与合计计算)。 */
    private BigDecimal discountAmount;
    /** 币种(可空,如 CNY)。 */
    private String currencyCode;
    /** 汇率(可空)。 */
    private BigDecimal exchangeRate;
    /** 单据状态。 */
    private String status;
    /** 制单人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 制单时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 审批人。 */
    private String approver;
    /** 审批时间。 */
    private LocalDateTime approvedAt;
    /** 驳回原因。 */
    private String rejectReason;
    /** 备注。 */
    private String remark;

}
