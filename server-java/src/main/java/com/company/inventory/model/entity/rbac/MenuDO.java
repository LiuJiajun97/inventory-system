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
 * 菜单表实体(表 sys_menu,目录/菜单/按钮三型合一)。
 *
 * @author inventory
 */
@TableName("sys_menu")
@Getter
@Setter
public class MenuDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 父节点 ID,0 表示顶级。 */
    private Long parentId;
    /** 菜单编码(唯一,button 类型即权限码)。 */
    private String menuCode;
    /** 菜单名称。 */
    private String menuName;
    /** 类型:directory / menu / button(常量见 MenuType)。 */
    private String type;
    /** 前端路由(目录/菜单有,button 为空)。 */
    private String path;
    /** 排序值(越小越靠前)。 */
    private Integer sort;
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
