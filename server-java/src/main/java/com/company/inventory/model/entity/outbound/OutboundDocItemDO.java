package com.company.inventory.model.entity.outbound;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 出库单行表实体(表 OutboundDocItem)。
 *
 * @author inventory
 */
@TableName("outbound_doc_item")
@Getter
@Setter
public class OutboundDocItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据 ID。 */
    private Long docId;
    /** 物品 ID。 */
    private Long itemId;
    /** 数量。 */
    private BigDecimal quantity;
    /** 批次 ID,无批次为 0。 */
    private Long batchId;
    /** 库位 ID,无库位为 0。 */
    private Long locationId;
    /** 序列号列表(JSON 字符串)。 */
    private String serialNos;

    /** 出库参考单价(销售发货携带订单行单价,可空)。 */
    private BigDecimal unitPrice;
    /** 关联销售订单行 ID(可空)。 */
    private Long refLineId;

}
