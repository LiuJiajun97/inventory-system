package com.company.inventory.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建用户入参(仅 admin 可操作)。
 *
 * @param username  用户名(至少 2 位)
 * @param password  密码(至少 6 位)
 * @param name      姓名
 * @param roleIds   角色 ID 列表(多角色,至少 1 个,首角色写入已废弃的 role 列保持兼容)
 * @param status    状态(可选,缺省 1)
 * @param warehouseIds 仓库授权 ID 列表(可选,缺省=不授权;空列表=不授权)
 * @author inventory
 */
public record UserCreateDTO(
        @NotBlank(message = "用户名必填")
        @Size(min = 2, message = "用户名至少 2 位") String username,
        @NotBlank(message = "密码必填")
        @Size(min = 6, message = "密码至少 6 位") String password,
        @NotBlank(message = "姓名必填") String name,
        @NotEmpty(message = "角色必填") List<Long> roleIds,
        Integer status,
        List<Long> warehouseIds) {
}
