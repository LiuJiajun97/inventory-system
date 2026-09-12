package com.company.inventory.model.dto.rbac;

/**
 * 更新角色入参(全部可选,非空才更新)。
 *
 * <p>内置角色禁改 roleCode(传值即 400);非内置角色 roleCode 传新值时校验唯一。</p>
 *
 * @param roleCode  角色编码(可选,内置角色禁改)
 * @param roleName  角色名称
 * @param remark    备注
 * @param status    状态:1 启用
 * @author inventory
 */
public record RoleUpdateDTO(
        String roleCode,
        String roleName,
        String remark,
        Integer status) {
}
