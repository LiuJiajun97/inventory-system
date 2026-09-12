package com.company.inventory.model.dto.rbac;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色-菜单全量替换入参(menuIds 为空数组表示清空该角色全部菜单)。
 *
 * @param menuIds 菜单 ID 列表
 * @author inventory
 */
public record RoleMenuDTO(
        @NotNull(message = "菜单列表必填(可为空数组)") List<Long> menuIds) {
}
