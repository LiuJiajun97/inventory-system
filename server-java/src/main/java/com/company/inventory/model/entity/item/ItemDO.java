package com.company.inventory.model.entity.item;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * 物品表实体(表 Item)。
 *
 * @author inventory
 */
@TableName("item")
@Getter
@Setter
public class ItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 物品编码(唯一)。 */
    private String itemCode;
    /** 物品名称。 */
    private String itemName;
    /** 单位。 */
    private String unit;
    /** 规格。 */
    private String spec;
    /** 扩展属性(JSON 字符串)。 */
    private String attributes;
    /** 物料分类(轻量单级,可空)。 */
    private String category;
    /** 最低库存预警线(可空,非空才参与低库存预警)。 */
    private BigDecimal minStock;
    /** 默认税率(百分数,默认 13.00,单据行取默认可改)。 */
    private BigDecimal defaultTaxRate;
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
