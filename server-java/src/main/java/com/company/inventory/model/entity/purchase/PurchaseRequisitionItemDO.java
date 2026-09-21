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
 * 请购单行实体(V25,表 purchase_requisition_item):无金额合计,行价可空不汇总。
 *
 * @author inventory
 */
@TableName("purchase_requisition_item")
@Getter
@Setter
public class PurchaseRequisitionItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 请购单 ID。 */
    private Long requisitionId;
    /** 行号。 */
    private Integer lineNo;
    /** 物品 ID。 */
    private Long itemId;
    /** 数量。 */
    private BigDecimal quantity;
    /** 期望到货日(可空)。 */
    private LocalDate expectedDate;
    /** 参考单价(可空,仅参考)。 */
    private BigDecimal unitPrice;
    /** 行备注。 */
    private String remark;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

}