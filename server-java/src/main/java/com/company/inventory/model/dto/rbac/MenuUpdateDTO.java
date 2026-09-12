package com.company.inventory.model.dto.rbac;

/**
 * 更新菜单入参(全部可选,非空才更新;menu_code 不可改)。
 *
 * @param parentId 父节点 ID(0 表示提升为顶级;不能改为自身或其子孙)
 * @param menuName 菜单名称
 * @param path     前端路由
 * @param sort     排序值
 * @param status   状态:1 启用
 * @author inventory
 */
public record MenuUpdateDTO(
        Long parentId,
        String menuName,
        String path,
        Integer sort,
        Integer status) {
}
