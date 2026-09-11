package com.company.inventory.model.vo.stock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存操作行(入库/出库共用,对应 stock-core 的 InboundLine/OutboundLine)。
 *
 * @author inventory
 */
@Getter
@Setter
public class StockLine {

    /** 物品 ID。 */
    private Long itemId;

    /** 数量(必须为正数)。 */
    private BigDecimal qty;

    /** 批次号(可选)。 */
    private String batchNo;

    /** 生产日期(入库可选)。 */
    private LocalDate productionDate;

    /** 保质期到期日(入库可选)。 */
    private LocalDate expiryDate;

    /** 供应商(入库可选)。 */
    private String supplier;

    /** 库位 ID(可选,缺省 0)。 */
    private Long locationId;

    /** 序列号列表(可选)。 */
    private List<String> serialNos;

    /** 调拨目的库位 ID(仅调拨使用,可选)。 */
    private Long toLocationId;

    /**
     * 构造空行,由调用方逐字段填充。
     */
    public StockLine() {
    }

}
