package com.company.inventory.model.entity.rbac;

import com.company.inventory.common.util.JsonbStringTypeHandler;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户表格偏好实体(表 sys_user_table_pref,按 (用户, 页面) 存 ProTable 列宽/显隐/列序)。
 *
 * <p>config 为 jsonb 列,以 JSON 文本形式存取(写前 Jackson 序列化,读后 Jackson 反序列化),
 * 服务端不做结构强校验,原文即前端序列化的 {widths, hidden, order}。</p>
 *
 * @author inventory
 */
@TableName("sys_user_table_pref")
@Getter
@Setter
public class UserTablePrefDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 页面标识(前端路由段,如 purchase-list)。 */
    private String pageKey;
    /** 列配置 JSON 原文:{widths 列宽, hidden 隐藏列, order 列序}(jsonb 列走 JDBC OTHER 类型传递)。 */
    @TableField(value = "config", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String config;
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
