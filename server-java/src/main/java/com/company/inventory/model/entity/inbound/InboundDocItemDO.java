package com.company.inventory.model.entity.inbound;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 入库单行表实体(表 InboundDocItem)。
 *
 * @author inventory
 */
@TableName("inbound_doc_item")
@Getter
@Setter
public class InboundDocItemDO {

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

    /** 入库不含税单价(采购到货携带订单行单价,可空)。 */
    private BigDecimal unitPrice;
    /** 税率(百分数,可空)。 */
    private BigDecimal taxRate;
    /** 批次号(新批次或已有批次,可空)。 */
    private String batchNo;
    /** 生产日期(建批次用,可空)。 */
    private java.time.LocalDate productionDate;
    /** 到期日期(建批次用,可空)。 */
    private java.time.LocalDate expiryDate;
    /** 关联采购订单行 ID(可空)。 */
    private Long refLineId;

    /**
     * 获取生产日期(建批次用,可空)。
     *
     * @return 生产日期(建批次用,可空)
     */
    public java.time.LocalDate getProductionDate() {
        return productionDate;
    }

    /**
     * 获取到期日期(建批次用,可空)。
     *
     * @return 到期日期(建批次用,可空)
     */
    public java.time.LocalDate getExpiryDate() {
        return expiryDate;
    }

}
