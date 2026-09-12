package com.company.inventory.model.entity.stocktake;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 盘点单行实体(表 StocktakeDocItem,bookQty 为系统快照,actualQty 为空=未盘)。
 *
 * @author inventory
 */
@TableName("stocktake_doc_item")
@Getter
@Setter
public class StocktakeDocItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 盘点单 ID。 */
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
    /** 账面快照数量。 */
    private BigDecimal bookQty;
    /** 实盘数量。 */
    private BigDecimal actualQty;
    /** 差异数量。 */
    private BigDecimal diffQty;
    /** 盘点人(可空)。 */
    private String checkerName;
    /** 盘点日期(可空)。 */
    private java.time.LocalDate checkDate;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

}
