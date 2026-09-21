package com.company.inventory.service.rbac.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.AuthCache;
import com.company.inventory.mapper.rbac.RoleMapper;
import com.company.inventory.mapper.rbac.RoleMenuMapper;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import com.company.inventory.model.dto.rbac.RoleCreateDTO;
import com.company.inventory.model.dto.rbac.RoleUpdateDTO;
import com.company.inventory.model.entity.rbac.RoleDO;
import com.company.inventory.model.entity.rbac.RoleMenuDO;
import com.company.inventory.model.entity.rbac.UserRoleDO;
import com.company.inventory.model.vo.rbac.RoleVO;
import com.company.inventory.service.rbac.RoleService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色管理服务实现。
 *
 * @author inventory
 */
@Service
public class RoleServiceImpl implements RoleService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(RoleServiceImpl.class);

    /** 角色启用状态值。 */
    private static final int STATUS_ENABLED = 1;

    /** 角色 Mapper。 */
    private final RoleMapper roleMapper;

    /** 角色-菜单绑定 Mapper。 */
    private final RoleMenuMapper roleMenuMapper;

    /** 用户-角色绑定 Mapper(有用户绑定禁删校验用)。 */
    private final UserRoleMapper userRoleMapper;

    /** 权限热点缓存(角色/菜单绑定变更后全清,先落库后清缓存,失败靠 TTL 兜底)。 */
    private final AuthCache authCache;

    /**
     * 构造服务。
     *
     * @param roleMapper     角色 Mapper
     * @param roleMenuMapper 角色-菜单绑定 Mapper
     * @param userRoleMapper 用户-角色绑定 Mapper
     * @param authCache      权限热点缓存
     */
    public RoleServiceImpl(RoleMapper roleMapper, RoleMenuMapper roleMenuMapper,
            UserRoleMapper userRoleMapper, AuthCache authCache) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.authCache = authCache;
    }

    /**
     * 角色列表(不分页,管理页用,按 id 升序)。
     *
     * @return 角色列表
     */
    @Override
    public List<RoleVO> list() {
        List<RoleDO> rows = roleMapper.selectList(
                new LambdaQueryWrapper<RoleDO>().orderByAsc(RoleDO::getId));
        return rows.stream().map(this::toVO).toList();
    }

    /**
     * 角色详情。
     *
     * @param id 角色 ID
     * @return 角色
     */
    @Override
    public RoleVO getById(long id) {
        return toVO(requireRole(id));
    }

    /**
     * 新建角色(role_code 唯一校验)。
     *
     * @param dto 入参
     * @return 新角色
     */
    @Override
    public RoleVO create(RoleCreateDTO dto) {
        long dup = roleMapper.selectCount(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getRoleCode, dto.roleCode().trim()));
        if (dup > 0) {
            throw new BizException("角色编码已存在", ErrorCode.ROLE_CODE_DUP,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        RoleDO role = new RoleDO();
        role.setRoleCode(dto.roleCode().trim());
        role.setRoleName(dto.roleName().trim());
        role.setRemark(dto.remark());
        role.setIsBuiltin(Boolean.FALSE);
        role.setStatus(STATUS_ENABLED);
        roleMapper.insert(role);
        LOGGER.info("新建角色: {}", role.getRoleCode());
        // 落库成功后全清权限缓存(先落库后清缓存,清理失败靠 TTL 兜底)
        authCache.evictByPrefix();
        return toVO(role);
    }

    /**
     * 更新角色(内置角色禁改 role_code;非内置 roleCode 变更时校验唯一)。
     *
     * @param id  角色 ID
     * @param dto 入参(可选字段,非空才更新)
     * @return 更新后角色
     */
    @Override
    public RoleVO update(long id, RoleUpdateDTO dto) {
        RoleDO role = requireRole(id);
        if (StringUtils.hasText(dto.roleCode())) {
            String newCode = dto.roleCode().trim();
            if (!newCode.equals(role.getRoleCode())) {
                if (Boolean.TRUE.equals(role.getIsBuiltin())) {
                    throw new BizException("内置角色编码不可修改", ErrorCode.ROLE_BUILTIN_PROTECTED,
                            ErrorCode.HTTP_BAD_REQUEST);
                }
                long dup = roleMapper.selectCount(new LambdaQueryWrapper<RoleDO>()
                        .eq(RoleDO::getRoleCode, newCode));
                if (dup > 0) {
                    throw new BizException("角色编码已存在", ErrorCode.ROLE_CODE_DUP,
                            ErrorCode.HTTP_BAD_REQUEST);
                }
                role.setRoleCode(newCode);
            }
        }
        if (StringUtils.hasText(dto.roleName())) {
            role.setRoleName(dto.roleName().trim());
        }
        if (dto.remark() != null) {
            role.setRemark(dto.remark());
        }
        if (dto.status() != null) {
            role.setStatus(dto.status());
        }
        roleMapper.updateById(role);
        LOGGER.info("更新角色: id={}", id);
        // 落库成功后全清权限缓存(先落库后清缓存,清理失败靠 TTL 兜底)
        authCache.evictByPrefix();
        return toVO(roleMapper.selectById(id));
    }

    /**
     * 删除角色(内置禁删,有用户绑定禁删;连带清 role_menu)。
     *
     * @param id 角色 ID
     */
    @Override
    @Transactional
    public void delete(long id) {
        RoleDO role = requireRole(id);
        if (Boolean.TRUE.equals(role.getIsBuiltin())) {
            throw new BizException("内置角色不可删除", ErrorCode.ROLE_BUILTIN_PROTECTED,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        long users = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getRoleId, id));
        if (users > 0) {
            throw new BizException("角色存在绑定用户,不可删除", ErrorCode.ROLE_HAS_USERS,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuDO>()
                .eq(RoleMenuDO::getRoleId, id));
        roleMapper.deleteById(id);
        LOGGER.info("删除角色: id={}", id);
        // 落库事务成功后全清权限缓存(先落库后清缓存,清理失败靠 TTL 兜底)
        authCache.evictByPrefix();
    }

    /**
     * 全量替换角色的菜单绑定(空数组 = 清空)。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 列表
     */
    @Override
    @Transactional
    public void assignMenus(long roleId, List<Long> menuIds) {
        requireRole(roleId);
        List<Long> safeIds = menuIds == null ? List.of() : menuIds;
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuDO>()
                .eq(RoleMenuDO::getRoleId, roleId));
        if (!safeIds.isEmpty()) {
            List<RoleMenuDO> rows = new ArrayList<>(safeIds.size());
            for (Long menuId : safeIds) {
                RoleMenuDO row = new RoleMenuDO();
                row.setRoleId(roleId);
                row.setMenuId(menuId);
                rows.add(row);
            }
            rows.forEach(roleMenuMapper::insert);
        }
        LOGGER.info("替换角色菜单: roleId={}, count={}", roleId, safeIds.size());
        // 落库事务成功后全清权限缓存(先落库后清缓存,清理失败靠 TTL 兜底)
        authCache.evictByPrefix();
    }

    /**
     * 查询角色的菜单 ID 列表(角色分配页回显用)。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    @Override
    public List<Long> menuIdsOf(long roleId) {
        requireRole(roleId);
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuDO>()
                        .eq(RoleMenuDO::getRoleId, roleId)
                        .orderByAsc(RoleMenuDO::getMenuId))
                .stream().map(RoleMenuDO::getMenuId).toList();
    }

    /**
     * 取角色,不存在抛 404。
     *
     * @param id 角色 ID
     * @return 角色实体
     */
    private RoleDO requireRole(long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色不存在");
        }
        return role;
    }

    /**
     * 实体转 VO。
     *
     * @param role 角色实体
     * @return VO
     */
    private RoleVO toVO(RoleDO role) {
        return new RoleVO(role.getId(), role.getRoleCode(), role.getRoleName(),
                role.getRemark(), role.getIsBuiltin(), role.getStatus(), role.getCreatedAt());
    }
}
