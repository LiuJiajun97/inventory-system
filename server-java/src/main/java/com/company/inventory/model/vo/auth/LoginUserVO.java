package com.company.inventory.model.vo.auth;

import java.util.List;

/**
 * 登录响应中的用户对象(契约:不含 createdAt)。
 *
 * @param id       用户 ID
 * @param username 用户名
 * @param name     姓名
 * @param role     主角色(首个,兼容旧前端单角色字段)
 * @param roles    角色编码列表(多角色并集,RBAC 新增)
 * @param status   状态
 * @author inventory
 */
public record LoginUserVO(Long id, String username, String name, String role,
        List<String> roles, Integer status) {
}
