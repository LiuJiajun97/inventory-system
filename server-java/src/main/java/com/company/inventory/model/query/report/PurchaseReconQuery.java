package com.company.inventory.model.query.report;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 采购对账查询条件(供应商维度)。
 *
 * <p>对应 GET /api/v1/reports/purchase-recon 的查询参数绑定;
 * from/to 为日期字符串(yyyy-MM-dd,空格分隔东八区约定),由服务层解析。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class PurchaseReconQuery extends PageQuery {

    /** 供应商 ID(可空)。 */
    private Long supplierId;

    /** 区间起(可空,含,yyyy-MM-dd,按采购单/退货单 doc_date)。 */
    private String from;

    /** 区间止(可空,含,yyyy-MM-dd,按采购单/退货单 doc_date)。 */
    private String to;
}
