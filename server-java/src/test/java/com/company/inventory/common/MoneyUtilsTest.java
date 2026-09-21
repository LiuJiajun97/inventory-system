package com.company.inventory.common;

import com.company.inventory.common.util.MoneyUtils;
import com.company.inventory.common.util.MoneyUtils.LineMoney;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MoneyUtils 价税重算测试:不含税正算/含税反算/数量零边界。
 *
 * @author inventory
 */
class MoneyUtilsTest {

    private static final BigDecimal R13 = new BigDecimal("13.00");

    /**
     * 用例 1:不含税正算——10 × 99.50 @13%:金额 995,税额 129.35,含税 1124.35,含税单价 112.435。
     */
    @Test
    void fromExclusiveUnit() {
        LineMoney lm = MoneyUtils.fromExclusiveUnit(new BigDecimal("10"), new BigDecimal("99.50"), R13);
        assertEquals(0, new BigDecimal("995").compareTo(lm.amount()));
        assertEquals(0, new BigDecimal("129.35").compareTo(lm.tax()));
        assertEquals(0, new BigDecimal("1124.35").compareTo(lm.inclusive()));
        assertEquals(0, new BigDecimal("99.50").compareTo(lm.unitPrice()));
        assertEquals(0, new BigDecimal("112.435").compareTo(lm.taxPrice()));
    }

    /**
     * 用例 2:含税反算——10 × 含税单价 113 @13%:含税 1130,金额 1000,税额 130,不含税单价 100。
     */
    @Test
    void fromInclusiveUnit() {
        LineMoney lm = MoneyUtils.fromInclusiveUnit(new BigDecimal("10"), new BigDecimal("113"), R13);
        assertEquals(0, new BigDecimal("1130").compareTo(lm.inclusive()));
        assertEquals(0, new BigDecimal("1000").compareTo(lm.amount()));
        assertEquals(0, new BigDecimal("130").compareTo(lm.tax()));
        assertEquals(0, new BigDecimal("100").compareTo(lm.unitPrice()));
        assertEquals(0, new BigDecimal("113").compareTo(lm.taxPrice()));
    }

    /**
     * 用例 3:含税反算除不尽时税额走差值(11.13 含税 @13%:金额=11.13/1.13=9.8496,税额=1.2804)。
     */
    @Test
    void fromInclusiveUnitRoundingDiff() {
        LineMoney lm = MoneyUtils.fromInclusiveUnit(new BigDecimal("1"), new BigDecimal("11.13"), R13);
        assertEquals(0, new BigDecimal("11.13").compareTo(lm.inclusive()));
        assertEquals(0, new BigDecimal("9.8496").compareTo(lm.amount()));
        // 税额 = 含税 - 金额(差值法),保证 金额+税额=含税 恒等
        assertEquals(0, lm.amount().add(lm.tax()).compareTo(lm.inclusive()));
        assertEquals(0, new BigDecimal("1.2804").compareTo(lm.tax()));
    }

    /**
     * 用例 4:数量为零时含税单价取 0(避免除零),金额/税额为 0,不含税单价原样返回。
     */
    @Test
    void zeroQty() {
        LineMoney lm = MoneyUtils.fromExclusiveUnit(BigDecimal.ZERO, new BigDecimal("10"), R13);
        assertEquals(0, BigDecimal.ZERO.compareTo(lm.amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(lm.tax()));
        assertEquals(0, BigDecimal.ZERO.setScale(4).compareTo(lm.taxPrice()));
    }

    /**
     * 用例 5:零税率时含税=不含税,税额 0。
     */
    @Test
    void zeroRate() {
        LineMoney lm = MoneyUtils.fromExclusiveUnit(new BigDecimal("5"), new BigDecimal("20"), BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("100").compareTo(lm.amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(lm.tax()));
        assertEquals(0, new BigDecimal("100").compareTo(lm.inclusive()));
        assertEquals(0, new BigDecimal("20").compareTo(lm.taxPrice()));
    }

    /**
     * 用例 6:正算与反算互逆——含税正算结果按含税单价反算,金额/税额/合计一致。
     */
    @Test
    void roundTrip() {
        LineMoney a = MoneyUtils.fromExclusiveUnit(new BigDecimal("7"), new BigDecimal("33.33"), R13);
        LineMoney b = MoneyUtils.fromInclusiveUnit(new BigDecimal("7"), a.taxPrice(), R13);
        assertEquals(0, a.amount().compareTo(b.amount()));
        assertEquals(0, a.inclusive().compareTo(b.inclusive()));
        // 税额允许 1 位末位差(两次独立舍入),但 金额+税额=合计 恒等
        assertEquals(0, b.amount().add(b.tax()).compareTo(b.inclusive()));
    }
}
