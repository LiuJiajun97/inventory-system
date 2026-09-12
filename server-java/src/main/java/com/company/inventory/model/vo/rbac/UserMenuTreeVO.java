package com.company.inventory.model.vo.rbac;

import java.util.List;

/**
 * 当前用户菜单树节点(前端导航 GET /auth/menus 用,仅目录+菜单,按钮折叠为权限码列表)。
 *
 * @param menuCode    菜单编码
 * @param menuName    菜单名称
 * @param path        前端路由
 * @param sort        排序值
 * @param permissions 该节点下按钮权限码列表
 * @param children    子节点
 * @author inventory
 */
public record UserMenuTreeVO(
        String menuCode,
        String menuName,
        String path,
        Integer sort,
        List<String> permissions,
        List<UserMenuTreeVO> children) {
}
