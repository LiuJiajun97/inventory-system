package com.company.inventory.model.dto.rbac;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建角色入参。
 *
 * @param roleCode  角色编码(唯一)
 * @param roleName  角色名称
 * @param remark    备注
 * @author inventory
 */
public record RoleCreateDTO(
        @NotBlank(message = "角色编码必填") String roleCode,
        @NotBlank(message = "角色名称必填") String roleName,
        String remark) {
}
