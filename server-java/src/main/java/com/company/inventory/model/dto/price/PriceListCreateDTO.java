package com.company.inventory.model.dto.price;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 价目表新建/编辑入参(V26:供应商/客户维度,行至少 1 条,同表同物品唯一)。
 *
 * @param ownerType  对方类型:supplier / customer
 * @param ownerId    对方 ID
 * @param name       价目名称(可空,默认对方单位简称)
 * @param validFrom  生效起(可空=不限)
 * @param validUntil 生效止(可空=不限)
 * @param lines      价目行(至少 1 条)
 * @author inventory
 */
public record PriceListCreateDTO(
        @NotBlank(message = "对方类型必填") String ownerType,
        @NotNull(message = "对方 ID 必填")
        @Positive(message = "对方 ID 必须为正数") Long ownerId,
        String name,
        LocalDate validFrom,
        LocalDate validUntil,
        @Valid
        @NotEmpty(message = "价目行至少 1 条") List<PriceListLineDTO> lines) {
}
