package com.company.inventory.vo.auth;

/**
 * 登录响应(契约:{token, user})。
 *
 * @param token JWT
 * @param user  用户对象
 * @author inventory
 */
public record LoginVO(String token, LoginUserVO user) {
}
