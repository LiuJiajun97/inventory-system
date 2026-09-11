package com.company.inventory.dto.user;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建用户入参(仅 admin 可操作)。
 *
 * @param username 用户名(至少 2 位)
 * @param password 密码(至少 6 位)
 * @param name     姓名
 * @param role     角色:admin / operator / viewer
 * @param status   状态(可选,缺省 1)
 * @author inventory
 */
public record UserCreateDTO(
        @NotBlank(message = "用户名必填")
        @Size(min = 2, message = "用户名至少 2 位") String username,
        @NotBlank(message = "密码必填")
        @Size(min = 6, message = "密码至少 6 位") String password,
        @NotBlank(message = "姓名必填") String name,
        @NotBlank(message = "角色必填") String role,
        Integer status) {
}
