package com.company.inventory.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 价税分离计算工具(方案 §2.0.2 拍板点 4:金额=数量×不含税单价,税额=金额×税率,价税合计=金额+税额)。
 *
 * <p>所有金额统一 NUMERIC(18,4) 口径(setScale 4,四舍五入);税率存百分数(13.00=13%)。
 * 禁止用 double 存金额。</p>
 *
 * @author inventory
 */
public final class MoneyUtils {

    /** 金额精度(与 NUMERIC(18,4) 对齐)。 */
    private static final int SCALE = 4;

    /** 百分数分母。 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 数字 1。 */
    private static final BigDecimal ONE = BigDecimal.ONE;

    /** 税率百分数化中间精度(tax_rate NUMERIC(6,4) ÷ 100 最多 8 位,取 10 位冗余防截断)。 */
    private static final int RATE_SCALE = 10;

    /**
     * 私有构造,工具类禁止实例化。
     */
    private MoneyUtils() {
    }

    /**
     * 行价税重算结果(V20 六列口径):不含税金额/税额/含税合计/不含税单价/含税单价。
     *
     * @param amount    不含税金额
     * @param tax       税额
     * @param inclusive 含税金额(=金额+税额)
     * @param unitPrice 不含税单价(4 位,四舍五入)
     * @param taxPrice  含税单价(=含税金额÷数量,4 位,四舍五入;数量为 0 时取 0)
     */
    public record LineMoney(BigDecimal amount, BigDecimal tax, BigDecimal inclusive,
            BigDecimal unitPrice, BigDecimal taxPrice) {
    }

    /**
     * 按不含税单价正算一行价税(V20:不含税路径)。
     *
     * <p>amount = qty × unitPrice;tax = amount × rate/100;inclusive = amount + tax;
     * 含税单价 = inclusive ÷ qty(数量为 0 时取 0)。全部 4 位小数四舍五入。</p>
     *
     * @param qty       数量
     * @param unitPrice 不含税单价
     * @param taxRate   税率(百分数,如 13.00)
     * @return 重算结果
     */
    public static LineMoney fromExclusiveUnit(BigDecimal qty, BigDecimal unitPrice, BigDecimal taxRate) {
        BigDecimal amount = amountOf(qty, unitPrice);
        BigDecimal tax = taxOf(amount, taxRate);
        BigDecimal inclusive = inclusiveOf(amount, tax);
        return new LineMoney(amount, tax, inclusive, unitPrice, unitPriceOf(inclusive, qty));
    }

    /**
     * 按含税单价反算一行价税(V20:含税路径)。
     *
     * <p>inclusive = qty × taxPrice;amount = inclusive ÷ (1 + rate/100);
     * tax = inclusive - amount;不含税单价 = amount ÷ qty(数量为 0 时取 0)。
     * 全部 4 位小数四舍五入;税额用差值算法避免两次独立舍入的误差。</p>
     *
     * @param qty      数量
     * @param taxPrice 含税单价
     * @param taxRate  税率(百分数,如 13.00)
     * @return 重算结果
     */
    public static LineMoney fromInclusiveUnit(BigDecimal qty, BigDecimal taxPrice, BigDecimal taxRate) {
        BigDecimal inclusive = amountOf(qty, taxPrice);
        BigDecimal divisor = ONE.add(taxRate.divide(HUNDRED, RATE_SCALE, RoundingMode.HALF_UP));
        BigDecimal amount = inclusive.divide(divisor, SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = inclusive.subtract(amount).setScale(SCALE, RoundingMode.HALF_UP);
        return new LineMoney(amount, tax, inclusive, unitPriceOf(amount, qty), taxPrice);
    }

    /**
     * 行单价 = 行金额合计 ÷ 数量(4 位,四舍五入;数量为空或 0 时取 0,避免除零)。
     *
     * @param total 行金额合计(不含税或含税,口径由调用方保证)
     * @param qty   数量
     * @return 单价
     */
    public static BigDecimal unitPriceOf(BigDecimal total, BigDecimal qty) {
        if (qty == null || qty.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return total.divide(qty, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 不含税金额 = 数量 × 不含税单价(4 位小数,四舍五入)。
     *
     * @param qty   数量
     * @param price 不含税单价
     * @return 金额
     */
    public static BigDecimal amountOf(BigDecimal qty, BigDecimal price) {
        return qty.multiply(price).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 税额 = 不含税金额 × 税率 ÷ 100(4 位小数,四舍五入)。
     *
     * @param amount  不含税金额
     * @param taxRate 税率(百分数,如 13.00)
     * @return 税额
     */
    public static BigDecimal taxOf(BigDecimal amount, BigDecimal taxRate) {
        return amount.multiply(taxRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 价税合计 = 不含税金额 + 税额。
     *
     * @param amount 不含税金额
     * @param tax    税额
     * @return 价税合计
     */
    public static BigDecimal inclusiveOf(BigDecimal amount, BigDecimal tax) {
        return amount.add(tax).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
