package com.company.inventory.model.entity.sales;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 销售报价单行实体(V25,表 sales_quotation_item),六列金额照抄销售订单行。
 *
 * @author inventory
 */
@TableName("sales_quotation_item")
@Getter
@Setter
public class SalesQuotationItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 报价单 ID。 */
    private Long quotationId;
    /** 行号。 */
    private Integer lineNo;
    /** 物品 ID。 */
    private Long itemId;
    /** 数量。 */
    private BigDecimal quantity;
    /** 不含税单价(可空)。 */
    private BigDecimal unitPrice;
    /** 含税单价(可空)。 */
    private BigDecimal taxPrice;
    /** 税率(百分数)。 */
    private BigDecimal taxRate;
    /** 不含税金额(服务端重算)。 */
    private BigDecimal amount;
    /** 税额(服务端重算)。 */
    private BigDecimal taxAmount;
    /** 价税合计(服务端重算)。 */
    private BigDecimal totalAmount;
    /** 行备注。 */
    private String remark;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

}