package com.company.inventory.model.entity.returns;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 销售退货单行实体(表 sales_return_item)。
 *
 * @author inventory
 */
@TableName("sales_return_item")
@Getter
@Setter
public class SalesReturnItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 退货单 ID。 */
    private Long docId;
    /** 行号(从 1 连号,服务端落)。 */
    private Integer lineNo;
    /** 原销售订单行 ID。 */
    private Long salesOrderItemId;
    /** 物品 ID。 */
    private Long itemId;
    /** 规格快照。 */
    private String specSnapshot;
    /** 单位快照。 */
    private String unit;
    /** 退货数量。 */
    private BigDecimal quantity;
    /** 不含税单价(原行快照,服务端取)。 */
    private BigDecimal unitPrice;

    /** 含税单价(V20,与 unit_price 二选一录入;服务端价税重算后落库)。 */
    private BigDecimal taxPrice;
    /** 税率(原行快照,服务端取)。 */
    private BigDecimal taxRate;
    /** 行金额快照(数量×不含税单价)。 */
    private BigDecimal amount;
    /** 行税额快照(金额×税率/100)。 */
    private BigDecimal taxAmount;
    /** 含税行金额快照(金额+税额)。 */
    private BigDecimal taxInclusiveTotal;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
}
