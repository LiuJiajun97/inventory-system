package com.company.inventory.model.entity.purchase;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * 采购订单行实体(表 PurchaseOrderItem)。
 *
 * @author inventory
 */
@TableName("purchase_order_item")
@Getter
@Setter
public class PurchaseOrderItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 订单 ID。 */
    private Long orderId;
    /** 行号。 */
    private Integer lineNo;
    /** 物品 ID。 */
    private Long itemId;
    /** 规格快照。 */
    private String specSnapshot;
    /** 单位快照。 */
    private String unit;
    /** 订购数量。 */
    private BigDecimal orderedQty;
    /** 累计到货数量。 */
    private BigDecimal arrivedQty;
    /** 累计退货数量(退货单过账回写,可退上限=arrived-returned)。 */
    private BigDecimal returnedQty;
    /** 计划交货日。 */
    private LocalDate expectedDeliveryDate;
    /** 不含税单价。 */
    private BigDecimal unitPrice;

    /** 含税单价(V20,与 unit_price 二选一录入;服务端价税重算后落库)。 */
    private BigDecimal taxPrice;
    /** 税率。 */
    private BigDecimal taxRate;
    /** 不含税金额。 */
    private BigDecimal amount;
    /** 税额。 */
    private BigDecimal taxAmount;
    /** 价税合计。 */
    private BigDecimal taxInclusiveTotal;
    /** 行是否关闭。 */
    private Boolean closed;
    /** 行备注。 */
    private String lineRemark;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

}
