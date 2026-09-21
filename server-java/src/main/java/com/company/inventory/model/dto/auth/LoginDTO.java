package com.company.inventory.model.dto.auth;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。
 *
 * @param username 用户名
 * @param password 密码
 * @author inventory
 */
public record LoginDTO(
        @NotBlank(message = "用户名必填") String username,
        @NotBlank(message = "密码必填") String password) {
}
