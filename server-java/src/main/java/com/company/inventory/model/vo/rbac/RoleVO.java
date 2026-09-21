package com.company.inventory.model.vo.rbac;

import java.time.LocalDateTime;

/**
 * 角色视图(列表/详情共用)。
 *
 * @param id        角色 ID
 * @param roleCode  角色编码
 * @param roleName  角色名称
 * @param remark    备注
 * @param isBuiltin 内置角色
 * @param status    状态:1 启用
 * @param createdAt 创建时间
 * @author inventory
 */
public record RoleVO(
        Long id,
        String roleCode,
        String roleName,
        String remark,
        Boolean isBuiltin,
        Integer status,
        LocalDateTime createdAt) {
}
