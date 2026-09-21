package com.company.inventory.model.entity.price;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 价目表行实体(表 price_list_item,物品不含税单价 + 税率可空,V26)。
 *
 * <p>唯一键 (price_list_id, item_id);税率 NULL=按物品默认税率。</p>
 *
 * @author inventory
 */
@TableName("price_list_item")
@Getter
@Setter
public class PriceListItemDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 价目表 ID。 */
    private Long priceListId;
    /** 物品 ID。 */
    private Long itemId;
    /** 不含税单价。 */
    private BigDecimal unitPrice;
    /** 税率(百分数,可空=NULL 按物品默认税率)。 */
    private BigDecimal taxRate;
    /** 创建人。 */
    private String creator;

}
