package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * 临期预警列表查询条件。
 *
 * @author inventory
 */
@Getter
@Setter
public class ExpiryAlertQuery extends PageQuery {

    /** 仓库 ID。 */
    @Positive(message = "仓库 ID必须为正数")
    private Long warehouseId;

    /** 物品 ID。 */
    @Positive(message = "物品 ID必须为正数")
    private Long itemId;


}
