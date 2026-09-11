package com.company.inventory.dto.auth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改自己密码入参。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码(至少 6 位)
 * @author inventory
 */
public record ChangePasswordDTO(
        @NotBlank(message = "原密码必填") String oldPassword,
        @NotNull(message = "新密码必填")
        @Size(min = 6, message = "新密码至少 6 位") String newPassword) {
}
