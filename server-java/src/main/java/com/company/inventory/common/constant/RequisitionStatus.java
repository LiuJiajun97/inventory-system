package com.company.inventory.common.constant;

/**
 * 请购单状态机常量(V25,无审批:draft/submitted/converted/cancelled)。
 *
 * @author inventory
 */
public final class RequisitionStatus {

    /** 草稿。 */
    public static final String DRAFT = "draft";

    /** 已提交(待处理)。 */
    public static final String SUBMITTED = "submitted";

    /** 已转换(转采购订单)。 */
    public static final String CONVERTED = "converted";

    /** 已取消(终态)。 */
    public static final String CANCELLED = "cancelled";

    private RequisitionStatus() {
    }
}