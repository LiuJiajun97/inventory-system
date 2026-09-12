package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import com.company.inventory.service.AuthService;
import com.company.inventory.model.vo.auth.LoginUserVO;
import com.company.inventory.model.vo.auth.LoginVO;

















import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;








import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 认证服务实现。
 *
 * @author inventory
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** 用户启用状态值。 */
    private static final int STATUS_ENABLED = 1;

    /** 密码编码器(BCrypt,强度 10,与 Fastify 版一致)。 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /** 用户 Mapper。 */
    private final UserMapper userMapper;

    /** 用户-角色绑定 Mapper(RBAC 多角色)。 */
    private final UserRoleMapper userRoleMapper;

    /** JWT 拦截器(复用其签发能力)。 */
    private final JwtInterceptor jwtInterceptor;

    /**
     * 构造服务。
     *
     * @param userMapper     用户 Mapper
     * @param userRoleMapper 用户-角色绑定 Mapper
     * @param jwtInterceptor JWT 拦截器
     */
    public AuthServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper,
            JwtInterceptor jwtInterceptor) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.jwtInterceptor = jwtInterceptor;
    }

    /**
     * 登录:用户不存在/停用/密码错误统一返回"用户名或密码错误"(401),防枚举。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录结果
     */
    @Override
    public LoginVO login(String username, String password) {
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username));
        if (user == null || !Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        // RBAC:角色取 sys_user_role 多角色并集;未绑定角色的存量用户回退读 role 列(兼容)
        List<String> roles = userRoleMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null || roles.isEmpty()) {
            if (StringUtils.hasText(user.getRole())) {
                roles = List.of(user.getRole());
            } else {
                roles = List.of();
            }
        }
        String primaryRole = roles.isEmpty() ? null : roles.get(0);
        String token = jwtInterceptor.issueToken(
                user.getId(), user.getUsername(), roles, user.getName());
        LOGGER.info("用户登录成功: {}", username);
        return new LoginVO(token, new LoginUserVO(
                user.getId(), user.getUsername(), user.getName(), primaryRole, roles,
                user.getStatus()));
    }

    /**
     * 修改密码:原密码错误抛 400,成功后更新 BCrypt 哈希。
     *
     * @param userId      当前用户 ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    @Override
    public void changePassword(long userId, String oldPassword, String newPassword) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException("原密码错误", ErrorCode.OLD_PASSWORD_WRONG, ErrorCode.HTTP_BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        LOGGER.info("用户修改密码成功: userId={}", userId);
    }
}
