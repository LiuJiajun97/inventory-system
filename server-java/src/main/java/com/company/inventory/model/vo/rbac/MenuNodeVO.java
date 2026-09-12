package com.company.inventory.model.vo.rbac;

import java.util.List;

/**
 * 全量菜单树节点(管理页 GET /api/v1/menus/tree 用,含 button 子节点)。
 *
 * @param id       菜单 ID
 * @param parentId 父节点 ID
 * @param menuCode 菜单编码
 * @param menuName 菜单名称
 * @param type     类型:directory / menu / button
 * @param path     前端路由
 * @param sort     排序值
 * @param status   状态
 * @param children 子节点
 * @author inventory
 */
public record MenuNodeVO(
        Long id,
        Long parentId,
        String menuCode,
        String menuName,
        String type,
        String path,
        Integer sort,
        Integer status,
        List<MenuNodeVO> children) {
}
