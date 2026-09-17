package com.company.inventory.model.entity.settlement;

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
 * 发票头表实体(表 Invoice,V18 结算域)。
 *
 * @author inventory
 */
@TableName("invoice")
@Getter
@Setter
public class InvoiceDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 发票号(FP-YYYYMMDD-NNNN,唯一)。 */
    private String docNo;

    /** 发票类型:purchase 采购票 | sales 销售票。 */
    private String invoiceType;

    /** 对方 ID:采购票=供应商 ID,销售票=客户 ID。 */
    private Long partyId;

    /** 发票日期。 */
    private LocalDate invoiceDate;

    /** 发票总额(行开票额合计,负票为负)。 */
    private BigDecimal totalAmount;

    /** 状态:draft | mismatch | confirmed | voided。 */
    private String status;

    /** 正负号:positive | negative。 */
    private String sign;

    /** 来源:manual 手工 | return_gen 退货生成。 */
    private String sourceType;

    /** return_gen 时指退货单 ID。 */
    private Long refReturnId;

    /** 备注。 */
    private String remark;

    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
