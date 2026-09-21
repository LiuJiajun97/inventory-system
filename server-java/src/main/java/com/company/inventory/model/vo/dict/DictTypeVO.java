package com.company.inventory.model.vo.dict;

/**
 * 字典类型 VO(含该类型下启用项数)。
 *
 * @author inventory
 */
public record DictTypeVO(
        Long id,
        String typeCode,
        String typeName,
        String remark,
        Integer status,
        Long enabledCount
) {
}
