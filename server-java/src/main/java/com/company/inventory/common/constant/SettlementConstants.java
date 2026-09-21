package com.company.inventory.common.constant;

import java.math.BigDecimal;

/**
 * 结算域(V18 三单匹配 + 应收应付)状态与类型常量。
 *
 * <p>发票状态机:draft(平账草稿)→ confirmed(确认);存在 |差异|>0.01 的行落 mismatch(挂起),
 * 改平后回 draft,作废 voided 留痕;仅 confirmed 发票进应收应付台账。</p>
 *
 * @author inventory
 */
public final class SettlementConstants {

    // ===== 发票类型 =====

    /** 发票类型:采购票(对方=供应商)。 */
    public static final String INVOICE_TYPE_PURCHASE = "purchase";

    /** 发票类型:销售票(对方=客户)。 */
    public static final String INVOICE_TYPE_SALES = "sales";

    // ===== 发票状态 =====

    /** 发票状态:草稿(全行平账,待确认)。 */
    public static final String INVOICE_STATUS_DRAFT = "draft";

    /** 发票状态:差异(存在 |差异|>0.01 的行,挂起,确认前必须改平)。 */
    public static final String INVOICE_STATUS_MISMATCH = "mismatch";

    /** 发票状态:已确认(进应收应付台账)。 */
    public static final String INVOICE_STATUS_CONFIRMED = "confirmed";

    /** 发票状态:已作废(留痕,释放已开票额度)。 */
    public static final String INVOICE_STATUS_VOIDED = "voided";

    // ===== 发票正负号 =====

    /** 正票。 */
    public static final String SIGN_POSITIVE = "positive";

    /** 负票(红字凭单,退货生成,确认后核减应付/应收)。 */
    public static final String SIGN_NEGATIVE = "negative";

    // ===== 发票来源 =====

    /** 来源:手工登记。 */
    public static final String SOURCE_MANUAL = "manual";

    /** 来源:退货过账自动生成。 */
    public static final String SOURCE_RETURN_GEN = "return_gen";

    // ===== 付款/收款 =====

    /** 付款单(对方=供应商,前缀 FK-)。 */
    public static final String PAY_TYPE_PAYMENT = "payment";

    /** 收款单(对方=客户,前缀 SK-)。 */
    public static final String PAY_TYPE_RECEIPT = "receipt";

    /** 付款/收款单状态:已确认(create 即生效)。 */
    public static final String PAYMENT_STATUS_CONFIRMED = "confirmed";

    /** 付款/收款单状态:已作废(释放核销额度)。 */
    public static final String PAYMENT_STATUS_VOIDED = "voided";

    // ===== 发票源单据类型(行级匹配) =====

    /** 源:采购关联入库行(仅采购票可挂)。 */
    public static final String SRC_INBOUND = "inbound";

    /** 源:销售关联出库行(仅销售票可挂)。 */
    public static final String SRC_OUTBOUND = "outbound";

    /** 源:采购退货单行(负票)。 */
    public static final String SRC_PURCHASE_RETURN = "purchase_return";

    /** 源:销售退货单行(负票)。 */
    public static final String SRC_SALES_RETURN = "sales_return";

    // ===== 差异与单号 =====

    /** 行差异容差:|开票额 - 源行含税额| ≤ 0.01 视为平账。 */
    public static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("0.01");

    /** 发票号前缀。 */
    public static final String PREFIX_INVOICE = "FP";

    /** 付款单号前缀。 */
    public static final String PREFIX_PAYMENT = "FK";

    /** 收款单号前缀。 */
    public static final String PREFIX_RECEIPT = "SK";

    private SettlementConstants() {
    }
}
