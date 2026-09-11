package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 低库存预警列表查询条件。
 *
 * @author inventory
 */
@Getter
@Setter
public class LowStockQuery extends PageQuery {

    /** 物品 ID。 */
    @Positive(message = "物品 ID必须为正数")
    private Long itemId;

    /** 物品编码或名称关键字。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String itemKeyword;


}
