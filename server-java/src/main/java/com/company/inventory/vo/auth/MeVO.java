package com.company.inventory.vo.auth;
/**
 * 当前登录用户信息(契约:{id, username, name, role})。
 *
 * @param id       用户 ID
 * @param username 用户名
 * @param name     姓名
 * @param role     角色
 * @author inventory
 */
public record MeVO(Long id, String username, String name, String role) {
}
