package com.company.inventory.service.rbac;

import com.company.inventory.model.dto.rbac.MenuCreateDTO;
import com.company.inventory.model.dto.rbac.MenuUpdateDTO;
import com.company.inventory.model.vo.rbac.MenuNodeVO;
import com.company.inventory.model.vo.rbac.UserMenuTreeVO;

import java.util.List;

/**
 * 菜单管理服务。
 *
 * @author inventory
 */
public interface MenuService {

    /**
     * 全量菜单树(含 button 子节点,admin 管理页用)。
     *
     * @return 顶级节点列表(按 sort 升序)
     */
    List<MenuNodeVO> tree();

    /**
     * 当前用户可见菜单树(仅目录+菜单,按钮折叠为权限码列表,前端导航用)。
     *
     * @param userId 用户 ID
     * @return 顶级节点列表(多角色取并集)
     */
    List<UserMenuTreeVO> userMenuTree(long userId);

    /**
     * 新建菜单(menu_code 唯一校验;父节点必须存在且为目录/菜单)。
     *
     * @param dto 入参
     * @return 新菜单节点(含空 children)
     */
    MenuNodeVO create(MenuCreateDTO dto);

    /**
     * 更新菜单(menu_code 不可改;父节点变更校验同新建)。
     *
     * @param id  菜单 ID
     * @param dto 入参(可选字段,非空才更新)
     * @return 更新后节点
     */
    MenuNodeVO update(long id, MenuUpdateDTO dto);

    /**
     * 删除菜单(有子节点禁删;连带清 role_menu 绑定)。
     *
     * @param id 菜单 ID
     */
    void delete(long id);
}
