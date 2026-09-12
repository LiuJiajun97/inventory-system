package com.company.inventory.model.dto.rbac;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建菜单入参。
 *
 * @param parentId  父节点 ID(可空,缺省 0;父节点必须是目录或菜单)
 * @param menuCode  菜单编码(唯一,button 类型即权限码)
 * @param menuName  菜单名称
 * @param type      类型:directory / menu / button
 * @param path      前端路由(directory/menu 有,button 为空)
 * @param sort      排序值
 * @author inventory
 */
public record MenuCreateDTO(
        Long parentId,
        @NotBlank(message = "菜单编码必填") String menuCode,
        @NotBlank(message = "菜单名称必填") String menuName,
        @NotBlank(message = "菜单类型必填") String type,
        String path,
        Integer sort) {
}
