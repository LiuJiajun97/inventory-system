package com.company.inventory.model.entity.user;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户表实体(表 User)。
 *
 * @author inventory
 */
@TableName("sys_user")
@Getter
@Setter
public class UserDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 用户名(唯一)。 */
    private String username;
    /** 密码哈希(BCrypt)。 */
    private String passwordHash;
    /** 姓名。 */
    private String name;
    /** 角色:admin / operator / viewer。 */
    private String role;
    /** 状态:1 启用。 */
    private Integer status;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间(UTC)。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
