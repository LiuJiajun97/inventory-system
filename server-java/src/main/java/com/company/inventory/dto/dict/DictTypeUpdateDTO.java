package com.company.inventory.dto.dict;

import jakarta.validation.constraints.NotBlank;

/**
 * 编辑字典类型入参(typeCode 不可改,不在 DTO 中暴露)。
 *
 * @author inventory
 */
public record DictTypeUpdateDTO(
        @NotBlank(message = "类型名称必填")
        String typeName,
        String remark,
        Integer status
) {
}
