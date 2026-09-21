package com.company.inventory.model.vo.dict;

/**
 * 字典项出参(管理界面用,含 id 和全部字段)。
 *
 * @param id        主键
 * @param dictType  字典类型
 * @param dictKey   字典键值
 * @param dictLabel 字典标签
 * @param sortOrder 排序号
 * @param status    状态:1 启用/0 停用
 * @author inventory
 */
public record DictVO(Long id, String dictType, String dictKey, String dictLabel,
        Integer sortOrder, Integer status) {
}
