package com.company.inventory.model.query.report;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 销售对账查询条件(客户维度)。
 *
 * <p>对应 GET /api/v1/reports/sales-recon 的查询参数绑定;
 * from/to 为日期字符串(yyyy-MM-dd,空格分隔东八区约定),由服务层解析。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class SalesReconQuery extends PageQuery {

    /** 客户 ID(可空)。 */
    private Long customerId;

    /** 区间起(可空,含,yyyy-MM-dd,按销售单/退货单 doc_date)。 */
    private String from;

    /** 区间止(可空,含,yyyy-MM-dd,按销售单/退货单 doc_date)。 */
    private String to;
}
