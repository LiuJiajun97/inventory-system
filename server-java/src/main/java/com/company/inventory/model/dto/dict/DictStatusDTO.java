package com.company.inventory.model.dto.dict;

import jakarta.validation.constraints.NotNull;

/**
 * 启用/停用字典项入参。
 *
 * @param status 状态:1 启用/0 停用
 * @author inventory
 */
public record DictStatusDTO(@NotNull(message = "状态必填") Integer status) {
}
