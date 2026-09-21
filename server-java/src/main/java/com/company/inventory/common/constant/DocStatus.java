package com.company.inventory.common.constant;

/**
 * 单据状态机常量(方案 §4:草稿→待审批→已审批→已完成/已关闭;驳回/作废分支)。
 *
 * <p>closed / completed / voided 为终态,整单只读。</p>
 *
 * @author inventory
 */
public final class DocStatus {

    /** 草稿。 */
    public static final String DRAFT = "draft";

    /** 待审批。 */
    public static final String PENDING = "pending";

    /** 已审批。 */
    public static final String APPROVED = "approved";

    /** 已完成(全部行累计量达标)。 */
    public static final String COMPLETED = "completed";

    /** 已关闭(手工关闭,剩余量作废)。 */
    public static final String CLOSED = "closed";

    /** 已驳回。 */
    public static final String REJECTED = "rejected";

    /** 已作废(终态)。 */
    public static final String VOIDED = "voided";

    private DocStatus() {
    }

    /**
     * 是否终态(completed/closed/voided,终态整单只读,不可再操作)。
     *
     * @param status 状态
     * @return true 表示终态
     */
    public static boolean isTerminal(String status) {
        return COMPLETED.equals(status) || CLOSED.equals(status) || VOIDED.equals(status);
    }
}
