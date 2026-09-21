package com.company.inventory.model.entity.rbac;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户-角色绑定实体(表 sys_user_role,联合主键无审计字段,多角色权限取并集)。
 *
 * @author inventory
 */
@TableName("sys_user_role")
@Getter
@Setter
public class UserRoleDO {

    /** 用户 ID。 */
    private Long userId;
    /** 角色 ID。 */
    private Long roleId;
}
