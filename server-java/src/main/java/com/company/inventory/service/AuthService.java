package com.company.inventory.service;

import com.company.inventory.model.vo.auth.LoginVO;





/**
 * 认证服务接口:登录、修改自己密码。
 *
 * @author inventory
 */
public interface AuthService {

    /**
     * 登录:校验用户名/密码/状态,签发 8 小时 JWT。
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
}
