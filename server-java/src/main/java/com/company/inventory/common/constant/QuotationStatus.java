package com.company.inventory.common.constant;

/**
 * 销售报价单状态机常量(V25,无审批:draft/sent/converted/voided)。
 *
 * @author inventory
 */
public final class QuotationStatus {

    /** 草稿。 */
    public static final String DRAFT = "draft";

    /** 已发送(报价送达)。 */
    public static final String SENT = "sent";

    /** 已转换(转销售订单)。 */
    public static final String CONVERTED = "converted";

    /** 已作废(终态)。 */
    public static final String VOIDED = "voided";

    private QuotationStatus() {
    }
}