package com.company.inventory.model.dto.user;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新用户入参(全部字段可选,只传了的字段被更新)。
 *
 * @param name          姓名
 * @param roleIds       角色 ID 列表(多角色;null = 不动,空列表 = 非法 400,首角色同步写 role 列)
 * @param warehouseIds  仓库授权 ID 列表(null = 不动,空列表 = 清空授权)
 * @param status        状态
 * @param password      密码(至少 6 位)
 * @author inventory
 */
public record UserUpdateDTO(
        String name,
        List<Long> roleIds,
        List<Long> warehouseIds,
        Integer status,
        @Size(min = 6, message = "密码至少 6 位") String password) {
}
