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

    /**
     * 私有构造,工具类禁止实例化。
     */
    private MoneyUtils() {
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
