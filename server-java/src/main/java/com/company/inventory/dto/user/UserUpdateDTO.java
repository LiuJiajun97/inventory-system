package com.company.inventory.dto.user;
import jakarta.validation.constraints.Size;

/**
 * 更新用户入参(全部字段可选,仅传了的字段被更新)。
 *
 * @param name     姓名
 * @param role     角色
 * @param status   状态
 * @param password 密码(至少 6 位)
 * @author inventory
 */
public record UserUpdateDTO(
        String name,
        String role,
        Integer status,
        @Size(min = 6, message = "密码至少 6 位") String password) {
}
