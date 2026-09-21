package com.company.inventory.model.entity.opening;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * 期初单行表实体(表 opening_stock_doc_item,数量/单价/批次等期初快照)。
 *
 * @author inventory
 */
@TableName("opening_stock_doc_item")
@Getter
@Setter
public class OpeningStockDocItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据 ID。 */
    private Long docId;
    /** 行号(从 1 连号,服务端落)。 */
    private Integer lineNo;
    /** 物品 ID。 */
    private Long itemId;
    /** 规格快照(取物品当前规格)。 */
    private String specSnapshot;
    /** 单位快照(取物品当前单位)。 */
    private String unit;
    /** 期初数量。 */
    private BigDecimal quantity;
    /** 期初成本参考单价(可选,仅快照,不涉及库存表)。 */
    private BigDecimal unitPrice;
    /** 批次号(批次/保质期仓必填,可空)。 */
    private String batchNo;
    /** 生产日期(可空)。 */
    private LocalDate productionDate;
    /** 保质期到期日(可空)。 */
    private LocalDate expiryDate;
    /** 库位 ID(库位仓必填,可空)。 */
    private Long locationId;
    /** 创建人。 */
    private String creator;

}
