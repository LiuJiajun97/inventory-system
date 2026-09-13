package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 发票列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/invoices 的查询参数绑定。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class InvoiceQuery extends PageQuery {

    /** 发票类型(可空,purchase | sales)。 */
    private String invoiceType;

    /** 对方 ID(可空,供应商/客户)。 */
    private Long partyId;

    /** 发票号关键字(可空,模糊)。 */
    private String docNo;

    /** 发票状态(可空,draft | mismatch | confirmed | voided)。 */
    private String status;

    /** 发票日期起(可空,yyyy-MM-dd)。 */
    private String from;

    /** 发票日期止(可空,yyyy-MM-dd)。 */
    private String to;
}
