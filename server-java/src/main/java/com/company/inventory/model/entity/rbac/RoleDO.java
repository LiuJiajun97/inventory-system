package com.company.inventory.model.entity.rbac;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 角色表实体(表 sys_role)。
 *
 * @author inventory
 */
@TableName("sys_role")
@Getter
@Setter
public class RoleDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 角色编码(唯一,如 admin)。 */
    private String roleCode;
    /** 角色名称。 */
    private String roleName;
    /** 备注。 */
    private String remark;
    /** 内置角色(禁删,禁改 role_code)。 */
    private Boolean isBuiltin;
    /** 状态:1 启用。 */
    private Integer status;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
