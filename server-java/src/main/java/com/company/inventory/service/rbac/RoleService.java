package com.company.inventory.service.rbac;

import com.company.inventory.model.dto.rbac.RoleCreateDTO;
import com.company.inventory.model.dto.rbac.RoleUpdateDTO;
import com.company.inventory.model.vo.rbac.RoleVO;

import java.util.List;

/**
 * 角色管理服务。
 *
 * @author inventory
 */
public interface RoleService {

    /**
     * 角色列表(不分页,管理页用,按 id 升序)。
     *
     * @return 角色列表
     */
    List<RoleVO> list();

    /**
     * 角色详情。
     *
     * @param id 角色 ID
     * @return 角色
     */
    RoleVO getById(long id);

    /**
     * 新建角色(role_code 唯一校验)。
     *
     * @param dto 入参
     * @return 新角色
     */
    RoleVO create(RoleCreateDTO dto);

    /**
     * 更新角色(内置角色禁改 role_code)。
     *
     * @param id  角色 ID
     * @param dto 入参(可选字段,非空才更新)
     * @return 更新后角色
     */
    RoleVO update(long id, RoleUpdateDTO dto);

    /**
     * 删除角色(内置禁删,有用户绑定禁删;连带清 role_menu)。
     *
     * @param id 角色 ID
     */
    void delete(long id);

    /**
     * 全量替换角色的菜单绑定(空数组 = 清空)。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 列表
     */
    void assignMenus(long roleId, List<Long> menuIds);

    /**
     * 查询角色的菜单 ID 列表(角色分配页回显用)。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    List<Long> menuIdsOf(long roleId);
}
