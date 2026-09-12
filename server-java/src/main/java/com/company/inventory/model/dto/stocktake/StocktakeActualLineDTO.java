package com.company.inventory.model.dto.stocktake;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 盘点实盘录入行(actualQty 为 null 表示该行取消实盘)。
 *
 * @param lineId      盘点行 ID
 * @param actualQty   实盘数量(可空=未盘)
 * @param checkerName 盘点人(可空)
 * @param checkDate   盘点日期(可空)
 * @author inventory
 */
public record StocktakeActualLineDTO(
        @NotNull(message = "盘点行 ID 必填") Long lineId,
        BigDecimal actualQty,
        String checkerName,
        LocalDate checkDate) {

    /** 兼容旧签名构造器(V9 新增字段默认 null,既有调用/测试不受影响)。 */
    public StocktakeActualLineDTO(Long lineId, BigDecimal actualQty) {
        this(lineId, actualQty, null, null);
    }
}
