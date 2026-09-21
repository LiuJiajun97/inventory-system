package com.company.inventory.mapper.rbac;

import com.company.inventory.model.entity.rbac.UserRoleDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户-角色绑定 Mapper:BaseMapper 简单 CRUD + 按用户取角色编码。
 *
 * @author inventory
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {

    /**
     * 查询指定用户的角色编码列表(sys_user_role join sys_role)。
     *
     * @param userId 用户 ID
     * @return 角色编码列表(可能为空)
     */
    @Select("SELECT r.role_code FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(@Param("userId") long userId);

    /**
     * 查询指定用户的角色 ID 列表。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表(可能为空)
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") long userId);

    /**
     * 查询指定用户的角色编码与名称列表(sys_user_role join sys_role,按 id 升序)。
     *
     * @param userId 用户 ID
     * @return 角色条目列表(role_code / role_name 键,可能为空)
     */
    @Select("SELECT r.role_code, r.role_name FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} ORDER BY r.id")
    List<Map<String, Object>> selectRoleInfosByUserId(@Param("userId") long userId);
}
