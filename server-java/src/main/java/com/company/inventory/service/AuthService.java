package com.company.inventory.service;

import com.company.inventory.model.vo.auth.LoginVO;

/**
 * 认证服务接口:登录、修改自己密码、登出吊销 token。
 *
 * @author inventory
 */
public interface AuthService {

    /**
     * 登录:校验用户名/密码/状态,签发 2 小时 JWT(含 jti,登出可吊销)。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录结果(token + 用户)
     */
    LoginVO login(String username, String password);

    /**
     * 修改当前用户密码:校验原密码后更新 BCrypt 哈希。
     *
     * @param userId      当前用户 ID(取自 token)
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    void changePassword(long userId, String oldPassword, String newPassword);

    /**
     * 登出:把当前 token 的 jti 拉黑(黑名单 TTL 取 token 剩余有效期,到期自动清除)。
     *
     * <p>旧 token(升级前签发、无 jti)传 null 时不做处理,自然过期。</p>
     *
     * @param jti        当前 token 的 JWT id(可为 null)
     * @param ttlSeconds 当前 token 剩余有效期(秒)
     */
    void logout(String jti, long ttlSeconds);
}
