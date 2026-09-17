package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.LoginGuard;
import com.company.inventory.common.support.TokenRevocationStore;
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

    /** 登录防爆破守卫。 */
    private final LoginGuard loginGuard;

    /** JWT 吊销黑名单(登出失效用)。 */
    private final TokenRevocationStore tokenRevocationStore;

    /**
     * 构造服务。
     *
     * @param userMapper           用户 Mapper
     * @param userRoleMapper       用户-角色绑定 Mapper
     * @param jwtInterceptor       JWT 拦截器
     * @param loginGuard           登录防爆破守卫
     * @param tokenRevocationStore JWT 吊销黑名单
     */
    public AuthServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper,
            JwtInterceptor jwtInterceptor, LoginGuard loginGuard,
            TokenRevocationStore tokenRevocationStore) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.jwtInterceptor = jwtInterceptor;
        this.loginGuard = loginGuard;
        this.tokenRevocationStore = tokenRevocationStore;
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
        // 防爆破:锁定中直接 429(含剩余秒数),不再碰密码校验
        loginGuard.checkLocked(username);
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username));
        if (user == null || !Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            loginGuard.recordFailure(username);
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginGuard.recordFailure(username);
            throw BizException.unauthorized("用户名或密码错误");
        }
        // 登录成功清零失败计数
        loginGuard.reset(username);
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

    /**
     * 登出:把当前 token 的 jti 拉黑,拉黑 TTL 取 token 剩余有效期(无 jti 的旧 token 跳过,自然过期)。
     *
     * @param jti        当前 token 的 JWT id(可为 null)
     * @param ttlSeconds 当前 token 剩余有效期(秒,签发时算好传入)
     */
    @Override
    public void logout(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            // token 已临期:不拉黑,自然过期
            return;
        }
        tokenRevocationStore.revoke(jti, ttlSeconds);
        LOGGER.info("用户登出,token 已吊销: jti={}, ttlSeconds={}", jti, ttlSeconds);
    }
}
