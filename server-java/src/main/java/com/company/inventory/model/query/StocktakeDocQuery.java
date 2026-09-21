package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 盘点单列表查询条件。
 *
 * @author inventory
 */
@Getter
@Setter
public class StocktakeDocQuery extends PageQuery {

    /** 仓库 ID。 */
    @Positive(message = "仓库 ID必须为正数")
    private Long warehouseId;

    /** 单号关键字。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String docNo;

    /** 单据状态。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String status;


}
