package com.company.inventory.service.rbac;

import com.company.inventory.common.support.AuthCache;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC 鉴权支撑服务:按 userId 取角色编码集(每请求查库)与按钮权限码集(走 Redis 权限缓存)。
 *
 * @author inventory
 */
@Service
public class RbacGuardService {

    /** 用户-角色 Mapper。 */
    private final UserRoleMapper userRoleMapper;

    /** 权限热点缓存(权限码 Redis 缓存 + miss 回源,原 SQL 逻辑在缓存回源内保留)。 */
    private final AuthCache authCache;

    /**
     * 构造服务。
     *
     * @param userRoleMapper 用户-角色 Mapper
     * @param authCache      权限热点缓存
     */
    public RbacGuardService(UserRoleMapper userRoleMapper, AuthCache authCache) {
        this.userRoleMapper = userRoleMapper;
        this.authCache = authCache;
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
     * 查询用户按钮权限码集合(多角色并集,type='button' 且启用):
     * 走 Redis 权限缓存,miss 回源 DB(原 SQL 逻辑),TTL 300 秒兜底。
     *
     * @param userId 用户 ID
     * @return 权限码集合(可能为空)
     */
    public Set<String> permissionCodesOf(long userId) {
        return authCache.permissionCodes(userId);
    }
}
