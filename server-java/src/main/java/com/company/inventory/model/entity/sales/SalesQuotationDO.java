package com.company.inventory.model.entity.sales;

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
 * 销售报价单表头实体(V25,表 sales_quotation):状态 draft/sent/converted/voided,无审批。
 *
 * @author inventory
 */
@TableName("sales_quotation")
@Getter
@Setter
public class SalesQuotationDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据编号(BJ-YYYYMMDD-NNNN)。 */
    private String docNo;
    /** 单据日期。 */
    private LocalDate docDate;
    /** 客户 ID。 */
    private Long customerId;
    /** 销售员用户 ID(可空)。 */
    private Long salespersonId;
    /** 发货仓库 ID。 */
    private Long warehouseId;
    /** 整单不含税合计。 */
    private BigDecimal totalAmount;
    /** 整单税额合计。 */
    private BigDecimal totalTaxAmount;
    /** 整单价税合计。 */
    private BigDecimal totalTaxInclusive;
    /** 报价有效期(可空)。 */
    private LocalDate quoteValidUntil;
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
    /** 备注。 */
    private String remark;

}