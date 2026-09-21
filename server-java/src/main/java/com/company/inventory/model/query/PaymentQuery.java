package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 付款/收款单列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/payments 的查询参数绑定。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class PaymentQuery extends PageQuery {

    /** 单据类型(可空,payment | receipt)。 */
    private String payType;

    /** 对方 ID(可空,供应商/客户)。 */
    private Long partyId;

    /** 单号关键字(可空,模糊)。 */
    private String docNo;

    /** 状态(可空,confirmed | voided)。 */
    private String status;

    /** 日期起(可空,yyyy-MM-dd)。 */
    private String from;

    /** 日期止(可空,yyyy-MM-dd)。 */
    private String to;
}
