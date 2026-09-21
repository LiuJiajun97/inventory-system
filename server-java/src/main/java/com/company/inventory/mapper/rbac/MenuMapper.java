package com.company.inventory.mapper.rbac;

import com.company.inventory.model.entity.rbac.MenuDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 Mapper:BaseMapper 简单 CRUD + 按用户角色取启用菜单(2 级 join)。
 *
 * @author inventory
 */
@Mapper
public interface MenuMapper extends BaseMapper<MenuDO> {

    /**
     * 查询指定用户(经 sys_user_role + sys_role_menu 并集)可见的启用菜单。
     *
     * @param userId 用户 ID
     * @return 菜单列表(按 sort/id 升序)
     */
    @Select("SELECT m.id, m.parent_id, m.menu_code, m.menu_name, m.type, m.path, m.sort, m.status "
            + "FROM sys_menu m "
            + "WHERE m.status = 1 "
            + "  AND m.id IN (SELECT rm.menu_id FROM sys_role_menu rm "
            + "               WHERE rm.role_id IN (SELECT ur.role_id FROM sys_user_role ur "
            + "                                   WHERE ur.user_id = #{userId})) "
            + "ORDER BY m.sort ASC, m.id ASC")
    List<MenuDO> selectMenusByUserId(@Param("userId") long userId);

    /**
     * 查询指定用户可见的按钮权限码集合(并集)。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    @Select("SELECT m.menu_code FROM sys_menu m "
            + "JOIN sys_role_menu rm ON rm.menu_id = m.id "
            + "WHERE m.type = 'button' AND m.status = 1 "
            + "  AND rm.role_id IN (SELECT ur.role_id FROM sys_user_role ur WHERE ur.user_id = #{userId})")
    List<String> selectPermissionCodesByUserId(@Param("userId") long userId);
}
