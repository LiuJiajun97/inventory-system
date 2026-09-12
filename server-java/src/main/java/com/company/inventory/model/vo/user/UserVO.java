package com.company.inventory.model.vo.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户出参(多角色 + 仓库授权,不含密码哈希)。
 *
 * @param id            主键
 * @param username      用户名
 * @param name          姓名
 * @param role          首角色编码(兼容旧前端,取角色列表首项)
 * @param status        状态
 * @param createdAt     创建时间
 * @param roles         角色列表(编码 + 名称)
 * @param warehouseIds  仓库授权 ID 列表(可能为空)
 * @author inventory
 */
public record UserVO(Long id, String username, String name, String role,
        Integer status, LocalDateTime createdAt, List<RoleItem> roles,
        List<Long> warehouseIds) {

    /**
     * 角色条目(编码 + 名称)。
     *
     * @param code 角色编码
     * @param name 角色名称
     * @author inventory
     */
    public record RoleItem(String code, String name) {
    }
}
