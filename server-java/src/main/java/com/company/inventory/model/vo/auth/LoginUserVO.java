package com.company.inventory.model.vo.auth;
/**
 * 登录响应中的用户对象(契约:不含 createdAt)。
 *
 * @param id       用户 ID
 * @param username 用户名
 * @param name     姓名
 * @param role     角色
 * @param status   状态
 * @author inventory
 */
public record LoginUserVO(Long id, String username, String name, String role, Integer status) {
}
