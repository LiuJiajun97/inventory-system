package com.company.inventory.dto.stocktake;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 盘点实盘录入入参。
 *
 * @param lines 实盘行(至少 1 行)
 * @author inventory
 */
public record StocktakeActualDTO(
        @Valid
        @NotEmpty(message = "至少 1 行") List<StocktakeActualLineDTO> lines) {
}
