package com.company.inventory.model.vo.dict;

/**
 * 字典项出参(下拉框用:{code, label})。
 *
 * @param code  字典键值(下拉 value)
 * @param label 字典标签(下拉显示文案)
 * @author inventory
 */
public record DictOptionVO(String code, String label) {
}
