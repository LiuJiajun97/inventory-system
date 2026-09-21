package com.company.inventory.common.constant;

/**
 * 站内消息类型常量(V25)。
 *
 * @author inventory
 */
public final class MessageType {

    /** 审批结果通知(approve/reject)。 */
    public static final String APPROVAL = "approval";

    /** 单据转换通知(报价→订单 / 请购→订单)。 */
    public static final String CONVERSION = "conversion";

    /** 每日预警汇总(daily-alert)。 */
    public static final String ALERT_DAILY = "alert_daily";

    private MessageType() {
    }
}