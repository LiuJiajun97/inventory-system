package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存冻结记录查询条件(V26,时间倒序分页)。
 *
 * @author inventory
 */
@Getter
@Setter
public class FreezeLogQuery extends PageQuery {

    /** 仓库 ID(可空)。 */
    private Long warehouseId;

    /** 批次号模糊关键词(可空)。 */
    private String batchNo;

}
