package com.company.inventory.service.rbac;

import com.company.inventory.mapper.rbac.MenuMapper;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC 鉴权支撑服务:按 userId 查库取角色编码集与按钮权限码集(单机规模每请求查 2 条 SQL,不加缓存)。
 *
 * @author inventory
 */
@Service
public class RbacGuardService {

    /** 用户-角色 Mapper。 */
    private final UserRoleMapper userRoleMapper;

    /** 菜单 Mapper。 */
    private final MenuMapper menuMapper;

    /**
     * 构造服务。
     *
     * @param userRoleMapper 用户-角色 Mapper
     * @param menuMapper     菜单 Mapper
     */
    public RbacGuardService(UserRoleMapper userRoleMapper, MenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.menuMapper = menuMapper;
    }

    /**
     * 查询用户角色编码集合(sys_user_role join sys_role)。
     *
     * @param userId 用户 ID
     * @return 角色编码集合(可能为空)
     */
    public Set<String> roleCodesOf(long userId) {
        List<String> codes = userRoleMapper.selectRoleCodesByUserId(userId);
        return codes == null ? new HashSet<>() : new HashSet<>(codes);
    }

    /**
     * 查询用户按钮权限码集合(多角色并集,type='button' 且启用)。
     *
     * @param userId 用户 ID
     * @return 权限码集合(可能为空)
     */
    public Set<String> permissionCodesOf(long userId) {
        List<String> codes = menuMapper.selectPermissionCodesByUserId(userId);
        return codes == null ? new HashSet<>() : new HashSet<>(codes);
    }
}
