package com.company.inventory.model.entity.settlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 核销行表实体(表 PaymentLine,V18 结算域):挂 confirmed 正票,支持部分核销。
 *
 * @author inventory
 */
@TableName("payment_line")
@Getter
@Setter
public class PaymentLineDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 付款/收款单 ID。 */
    private Long paymentId;

    /** 发票 ID(仅正票)。 */
    private Long invoiceId;

    /** 核销额(正数)。 */
    private BigDecimal amount;
}
