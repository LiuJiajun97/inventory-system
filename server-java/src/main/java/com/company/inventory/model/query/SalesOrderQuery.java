package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 销售订单列表查询条件。
 *
 * @author inventory
 */
@Getter
@Setter
public class SalesOrderQuery extends PageQuery {

    /** 客户 ID。 */
    @Positive(message = "客户 ID必须为正数")
    private Long customerId;

    /** 单号关键字。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String docNo;

    /** 单据状态。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String status;

    /** 单据日期起(yyyy-MM-dd)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String from;

    /** 单据日期止(yyyy-MM-dd)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String to;


}
