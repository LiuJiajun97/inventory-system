package com.company.inventory.model.vo.user;
import java.time.LocalDateTime;

/**
 * 用户出参(契约:6 字段,不含密码哈希)。
 *
 * @param id        主键
 * @param username  用户名
 * @param name      姓名
 * @param role      角色
 * @param status    状态
 * @param createdAt 创建时间
 * @author inventory
 */
public record UserVO(Long id, String username, String name, String role,
        Integer status, LocalDateTime createdAt) {
}
