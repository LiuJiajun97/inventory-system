package com.company.inventory.dto.dict;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 新建字典类型入参。
 *
 * @author inventory
 */
public record DictTypeCreateDTO(
        @NotBlank(message = "类型编码必填")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "类型编码只能含小写字母、数字、下划线,且以字母开头")
        String typeCode,
        @NotBlank(message = "类型名称必填")
        String typeName,
        String remark
) {
}
