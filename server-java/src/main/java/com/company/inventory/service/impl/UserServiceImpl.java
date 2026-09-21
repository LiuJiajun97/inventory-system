package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.AuthCache;
import com.company.inventory.model.dto.user.UserCreateDTO;
import com.company.inventory.model.dto.user.UserUpdateDTO;
import com.company.inventory.model.entity.rbac.RoleDO;
import com.company.inventory.model.entity.rbac.UserRoleDO;
import com.company.inventory.model.entity.rbac.UserWarehouseDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.mapper.rbac.RoleMapper;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import com.company.inventory.mapper.rbac.UserWarehouseMapper;
import com.company.inventory.model.query.UserQuery;
import com.company.inventory.service.UserService;
import com.company.inventory.model.vo.user.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务实现(多角色 + 仓库授权,写 sys_user_role / sys_user_warehouse,
 * 已废弃的 sys_user.role 列同步维护为首角色保持非空,二期再删)。
 *
 * @author inventory
 */
@Service
public class UserServiceImpl implements UserService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 密码编码器(BCrypt,强度 10,与 Fastify 版一致)。 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /** 用户 Mapper。 */
    private final UserMapper userMapper;

    /** 用户-角色 Mapper。 */
    private final UserRoleMapper userRoleMapper;

    /** 用户-仓库 Mapper。 */
    private final UserWarehouseMapper userWarehouseMapper;

    /** 角色 Mapper。 */
    private final RoleMapper roleMapper;

    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;

    /** 权限热点缓存(角色/仓库授权变更后全清,先落库后清缓存,失败靠 TTL 兜底)。 */
    private final AuthCache authCache;

    /**
     * 构造服务。
     *
     * @param userMapper          用户 Mapper
     * @param userRoleMapper      用户-角色 Mapper
     * @param userWarehouseMapper 用户-仓库 Mapper
     * @param roleMapper          角色 Mapper
     * @param warehouseMapper     仓库 Mapper
     * @param authCache           权限热点缓存
     */
    public UserServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper,
            UserWarehouseMapper userWarehouseMapper, RoleMapper roleMapper,
            WarehouseMapper warehouseMapper, AuthCache authCache) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.userWarehouseMapper = userWarehouseMapper;
        this.roleMapper = roleMapper;
        this.warehouseMapper = warehouseMapper;
        this.authCache = authCache;
    }

    /**
     * 用户分页列表(按 id 升序,多角色 + 仓库授权,不含密码)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<UserVO> list(UserQuery query) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String like = query.getKeyword().trim();
            wrapper.and(w -> w.like(UserDO::getUsername, like)
                    .or().like(UserDO::getName, like));
        }
        wrapper.orderByAsc(UserDO::getId);
        Page<UserDO> page = userMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<UserVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 新建用户(多角色,首角色写入已废弃的 role 列)。
     *
     * <p>落库事务成功后全清权限缓存(先落库后清缓存,清理失败靠 TTL 兜底)。</p>
     *
     * @param dto 入参
     * @return 新用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateDTO dto) {
        validateRoleIds(dto.roleIds());
        Long exist = userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, dto.username()));
        if (exist != null && exist > 0) {
            throw new BizException("用户名已存在", ErrorCode.USERNAME_DUP, ErrorCode.HTTP_BAD_REQUEST);
        }
        // role 列(deprecated,NOT NULL)先写首角色,upsert 后与绑定保持一致
        String primaryRole = resolveRoleCodes(dto.roleIds())
                .get(dto.roleIds().get(0)).getRoleCode();
        UserDO user = new UserDO();
        user.setUsername(dto.username());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setName(dto.name());
        user.setRole(primaryRole);
        user.setStatus(dto.status() == null ? 1 : dto.status());
        userMapper.insert(user);
        upsertUserRoles(user.getId(), dto.roleIds());
        if (dto.warehouseIds() != null && !dto.warehouseIds().isEmpty()) {
            upsertUserWarehouses(user.getId(), dto.warehouseIds());
        }
        LOGGER.info("新建用户: username={}, roleIds={}, warehouseIds={}",
                dto.username(), dto.roleIds(), dto.warehouseIds());
        authCache.evictByPrefix();
        return toVO(userMapper.selectById(user.getId()));
    }

    /**
     * 更新用户(部分字段;roleIds/warehouseIds 先删后插 upsert)。
     *
     * <p>落库事务成功后全清权限缓存(先落库后清缓存,清理失败靠 TTL 兜底)。</p>
     *
     * @param id  用户 ID
     * @param dto 入参
     * @return 更新后用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO update(long id, UserUpdateDTO dto) {
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (dto.roleIds() != null) {
            if (dto.roleIds().isEmpty()) {
                throw new BizException("角色不能为空", ErrorCode.BAD_ROLE,
                        ErrorCode.HTTP_BAD_REQUEST);
            }
            validateRoleIds(dto.roleIds());
            upsertUserRoles(id, dto.roleIds());
        }
        if (dto.warehouseIds() != null) {
            upsertUserWarehouses(id, dto.warehouseIds());
        }
        if (StringUtils.hasText(dto.name())) {
            user.setName(dto.name());
        }
        if (dto.status() != null) {
            user.setStatus(dto.status());
        }
        if (StringUtils.hasText(dto.password())) {
            user.setPasswordHash(passwordEncoder.encode(dto.password()));
        }
        // role 列(deprecated)与角色绑定保持一致:取角色列表首角色编码
        List<UserVO.RoleItem> roles = selectRoles(id);
        if (!roles.isEmpty()) {
            user.setRole(roles.get(0).code());
        }
        userMapper.updateById(user);
        LOGGER.info("更新用户: userId={}, fields={name={}, roleIds={}, warehouseIds={}, status={}, password={}}",
                id, StringUtils.hasText(dto.name()), dto.roleIds(), dto.warehouseIds(),
                dto.status(), StringUtils.hasText(dto.password()));
        authCache.evictByPrefix();
        return toVO(userMapper.selectById(id));
    }

    /**
     * 校验角色 ID 列表均存在。
     *
     * @param roleIds 角色 ID 列表
     */
    private void validateRoleIds(List<Long> roleIds) {
        resolveRoleCodes(roleIds);
    }

    /**
     * 查询并校验角色 ID 列表均存在(空集合防护)。
     *
     * @param roleIds 角色 ID 列表(非空)
     * @return 角色 ID 到实体映射
     */
    private Map<Long, RoleDO> resolveRoleCodes(
            List<Long> roleIds) {
        // 空集合防护:selectByIds 空集生成非法 SQL
        if (roleIds.isEmpty()) {
            throw new BizException("角色不能为空", ErrorCode.BAD_ROLE,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        Map<Long, RoleDO> roleMap = roleMapper
                .selectByIds(roleIds).stream()
                .collect(Collectors.toMap(
                        RoleDO::getId,
                        Function.identity()));
        if (roleMap.size() != roleIds.stream().distinct().count()) {
            throw new BizException("角色不存在或重复", ErrorCode.BAD_ROLE,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        return roleMap;
    }

    /**
     * upsert 用户-角色绑定(先删后插,事务内),并同步已废弃的 role 列。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表(非空)
     */
    private void upsertUserRoles(long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId));
        for (Long roleId : roleIds.stream().distinct().toList()) {
            UserRoleDO binding = new UserRoleDO();
            binding.setUserId(userId);
            binding.setRoleId(roleId);
            userRoleMapper.insert(binding);
        }
    }

    /**
     * upsert 用户-仓库授权(先删后插,事务内,空列表 = 清空授权)。
     *
     * @param userId       用户 ID
     * @param warehouseIds 仓库 ID 列表(可为空)
     */
    private void upsertUserWarehouses(long userId, List<Long> warehouseIds) {
        // 空集合防护:selectByIds 空集生成非法 SQL
        if (!warehouseIds.isEmpty()) {
            long found = warehouseMapper.selectByIds(warehouseIds).size();
            if (found != warehouseIds.stream().distinct().count()) {
                throw new BizException("仓库不存在或重复", ErrorCode.VALIDATION_ERROR,
                        ErrorCode.HTTP_BAD_REQUEST);
            }
        }
        userWarehouseMapper.delete(new LambdaQueryWrapper<UserWarehouseDO>()
                .eq(UserWarehouseDO::getUserId, userId));
        for (Long warehouseId : warehouseIds.stream().distinct().toList()) {
            UserWarehouseDO grant = new UserWarehouseDO();
            grant.setUserId(userId);
            grant.setWarehouseId(warehouseId);
            userWarehouseMapper.insert(grant);
        }
    }

    /**
     * 查询用户角色列表(编码 + 名称)。
     *
     * @param userId 用户 ID
     * @return 角色条目列表(可能为空)
     */
    private List<UserVO.RoleItem> selectRoles(long userId) {
        List<Map<String, Object>> rows = userRoleMapper.selectRoleInfosByUserId(userId);
        return rows.stream()
                .map(r -> new UserVO.RoleItem(String.valueOf(r.get("role_code")),
                        String.valueOf(r.get("role_name"))))
                .toList();
    }

    /**
     * 实体转 VO(多角色 + 仓库授权,不含密码;role 列取角色列表首项兼容旧前端)。
     *
     * @param user 实体
     * @return VO
     */
    private UserVO toVO(UserDO user) {
        List<UserVO.RoleItem> roles = selectRoles(user.getId());
        List<Long> warehouseIds = userWarehouseMapper.selectWarehouseIdsByUserId(user.getId());
        String primaryRole = roles.isEmpty() ? user.getRole() : roles.get(0).code();
        return new UserVO(user.getId(), user.getUsername(), user.getName(),
                primaryRole, user.getStatus(), user.getCreatedAt(), roles,
                warehouseIds == null ? List.of() : warehouseIds);
    }
}
