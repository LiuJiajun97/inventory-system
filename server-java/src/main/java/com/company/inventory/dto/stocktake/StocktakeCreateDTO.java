package com.company.inventory.dto.stocktake;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建盘点单入参(scopeType all=全仓,item=指定物品;保存即按当前余额生成 bookQty 快照)。
 *
 * @param warehouseId 仓库 ID
 * @param docDate     单据日期
 * @param scopeType   盘点范围(all/item)
 * @param itemIds     指定物品 ID 列表(scopeType=item 时必填)
 * @param remark      备注
 * @author inventory
 */
public record StocktakeCreateDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        @NotNull(message = "单据日期必填") LocalDate docDate,
        String scopeType,
        List<Long> itemIds,
        String remark) {
}
