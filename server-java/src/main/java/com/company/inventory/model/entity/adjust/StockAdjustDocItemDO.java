package com.company.inventory.model.entity.adjust;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存调整单行实体(表 StockAdjustDocItem,qty 为绝对值,方向由 adjustType 决定)。
 *
 * @author inventory
 */
@TableName("stock_adjust_doc_item")
@Getter
@Setter
public class StockAdjustDocItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 调整单 ID。 */
    private Long docId;
    /** 行号。 */
    private Integer lineNo;
    /** 物品 ID。 */
    private Long itemId;
    /** 规格快照。 */
    private String specSnapshot;
    /** 单位快照。 */
    private String unit;
    /** 批次 ID。 */
    private Long batchId;
    /** 库位 ID。 */
    private Long locationId;
    /** 调整数量。 */
    private BigDecimal qty;
    /** 成本参考价。 */
    private BigDecimal unitPrice;
    /** 原因。 */
    private String reason;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

}
