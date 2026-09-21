package com.company.inventory.model.entity.settlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 发票行表实体(表 InvoiceItem,V18 结算域):行级匹配挂源单据行。
 *
 * @author inventory
 */
@TableName("invoice_item")
@Getter
@Setter
public class InvoiceItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 发票 ID。 */
    private Long invoiceId;

    /** 行号(从 1 连号)。 */
    private Integer lineNo;

    /** 源单据类型:inbound | outbound | purchase_return | sales_return。 */
    private String srcDocType;

    /** 源单据 ID。 */
    private Long srcDocId;

    /** 源单据行 ID。 */
    private Long srcDocItemId;

    /** 物品 ID。 */
    private Long itemId;

    /** 规格快照。 */
    private String specSnapshot;

    /** 单位。 */
    private String unit;

    /** 数量(源行数量,正数;正负号由 sign/金额体现)。 */
    private BigDecimal quantity;

    /** 开票额(负票为负数)。 */
    private BigDecimal invoicedAmount;

    /** 源单据行含税额快照(负票为负数)。 */
    private BigDecimal srcAmount;

    /** 差异额=开票额-源行含税额。 */
    private BigDecimal variance;

    /** 正负号(冗余头表,参与唯一约束防同一行同向重复挂票)。 */
    private String sign;

    /** 批次号(可空)。 */
    private String batchNo;
}
