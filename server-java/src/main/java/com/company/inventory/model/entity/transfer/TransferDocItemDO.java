package com.company.inventory.model.entity.transfer;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 调拨单行实体(表 TransferDocItem)。
 *
 * @author inventory
 */
@TableName("transfer_doc_item")
@Getter
@Setter
public class TransferDocItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 调拨单 ID。 */
    private Long docId;
    /** 行号。 */
    private Integer lineNo;
    /** 物品 ID。 */
    private Long itemId;
    /** 规格快照。 */
    private String specSnapshot;
    /** 单位快照。 */
    private String unit;
    /** 调拨数量。 */
    private BigDecimal qty;
    /** 成本参考单价。 */
    private BigDecimal unitPrice;
    /** 源库位 ID。 */
    private Long fromLocationId;
    /** 目的库位 ID。 */
    private Long toLocationId;
    /** 行备注。 */
    private String lineRemark;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;

}
