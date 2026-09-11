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
 * 字典表实体(表 Dict):主数据下拉枚举(仓库类型/物品分类/结算方式等)。
 *
 * <p>状态机枚举(单据 status/bizCode/adjustType)不进字典,保持常量类。</p>
 *
 * @author inventory
 */
@TableName("dict")
@Getter
@Setter
public class DictDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 字典类型:warehouseType / itemCategory / settleMethod。 */
    private String dictType;
    /** 字典键值(下拉 value,如 raw/finished)。 */
    private String dictKey;
    /** 字典标签(下拉显示文案,如 原材料仓)。 */
    private String dictLabel;
    /** 排序号(升序)。 */
    private Integer sortOrder;
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
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
