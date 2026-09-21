package com.company.inventory.model.entity.rbac;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 角色-菜单绑定实体(表 sys_role_menu,联合主键无审计字段)。
 *
 * @author inventory
 */
@TableName("sys_role_menu")
@Getter
@Setter
public class RoleMenuDO {

    /** 角色 ID。 */
    private Long roleId;
    /** 菜单 ID。 */
    private Long menuId;
}
