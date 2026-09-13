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
 * 付款单/收款单头表实体(表 PaymentDoc,V18 结算域):create 即生效。
 *
 * @author inventory
 */
@TableName("payment_doc")
@Getter
@Setter
public class PaymentDocDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 单号(付款 FK-YYYYMMDD-NNNN / 收款 SK-YYYYMMDD-NNNN,唯一)。 */
    private String docNo;

    /** 单据类型:payment 付款 | receipt 收款。 */
    private String payType;

    /** 对方 ID:付款=供应商 ID,收款=客户 ID。 */
    private Long partyId;

    /** 付款/收款日期。 */
    private LocalDate payDate;

    /** 总额(核销行合计)。 */
    private BigDecimal totalAmount;

    /** 状态:confirmed | voided。 */
    private String status;

    /** 备注。 */
    private String remark;

    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

    /** 创建时间(UTC)。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    /** 修改时间(UTC)。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
