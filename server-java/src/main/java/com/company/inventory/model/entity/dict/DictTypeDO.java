package com.company.inventory.model.entity.dict;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 字典类型表实体(表 DictType):管理字典分类(仓库类型/物品分类等)。
 *
 * @author inventory
 */
@TableName("dict_type")
@Getter
@Setter
public class DictTypeDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 类型编码(唯一,不可改)。 */
    private String typeCode;
    /** 类型名称。 */
    private String typeName;
    /** 备注。 */
    private String remark;
    /** 状态:1 启用 / 0 停用。 */
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
    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
