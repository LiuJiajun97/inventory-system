package com.company.inventory.dto.dict;

/**
 * 编辑字典项入参(仅 dictLabel/sortOrder 可改,dictType/dictKey 不可改)。
 *
 * @param dictLabel 字典标签(下拉显示文案,可空不改)
 * @param sortOrder 排序号(可空不改)
 * @author inventory
 */
public record DictUpdateDTO(String dictLabel, Integer sortOrder) {
}
