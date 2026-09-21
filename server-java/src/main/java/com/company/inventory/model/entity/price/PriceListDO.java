package com.company.inventory.model.entity.price;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 价目表实体(表 price_list,供应商/客户维度的生效价目,V26)。
 *
 * <p>命中规则:owner_type+owner_id+物品+日期在 [validFrom, validUntil] 内
 * (边界任一可空=不限)且 status=1;同物品多条命中取 validFrom 最近一条。</p>
 *
 * @author inventory
 */
@TableName("price_list")
@Getter
@Setter
public class PriceListDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 对方类型:supplier 供应商 / customer 客户。 */
    private String ownerType;
    /** 对方 ID(供应商/客户表主键)。 */
    private Long ownerId;
    /** 价目名称(可空,默认对方单位简称)。 */
    private String name;
    /** 生效起(可空=不限)。 */
    private LocalDate validFrom;
    /** 生效止(可空=不限)。 */
    private LocalDate validUntil;
    /** 状态:1 启用 / 0 停用(停用不参与命中)。 */
    private Integer status;
    /** 创建人。 */
    private String creator;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新人。 */
    private String updater;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

}
