package com.company.inventory.model.dto.dict;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建字典项入参。
 *
 * @param dictType  字典类型(warehouseType/itemCategory/settleMethod)
 * @param dictKey   字典键值(下拉 value,同一 dictType 内唯一)
 * @param dictLabel 字典标签(下拉显示文案)
 * @param sortOrder 排序号(可空默认 0)
 * @param status    状态:1 启用/0 停用(可空默认 1)
 * @author inventory
 */
public record DictCreateDTO(
        @NotBlank(message = "字典类型必填") String dictType,
        @NotBlank(message = "字典键值必填") String dictKey,
        @NotBlank(message = "字典标签必填") String dictLabel,
        Integer sortOrder,
        Integer status) {
}
