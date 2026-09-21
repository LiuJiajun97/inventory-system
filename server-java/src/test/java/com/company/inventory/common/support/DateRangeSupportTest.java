package com.company.inventory.common.support;

import com.company.inventory.common.exception.BizException;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 列表查询日期/时间区间参数解析测试:空格/T/纯日期/Z 分隔统一解析与格式校验。
 *
 * @author inventory
 */
class DateRangeSupportTest {

    @Test
    void parseDate_blankReturnsNull() {
        assertNull(DateRangeSupport.parseDate(null, "日期起"));
        assertNull(DateRangeSupport.parseDate("", "日期起"));
        assertNull(DateRangeSupport.parseDate("   ", "日期起"));
    }

    @Test
    void parseDate_validFormats() {
        assertEquals(LocalDate.of(2026, 9, 10), DateRangeSupport.parseDate("2026-09-10", "日期起"));
        // 前后空白容错
        assertEquals(LocalDate.of(2026, 9, 10), DateRangeSupport.parseDate(" 2026-09-10 ", "日期起"));
    }

    @Test
    void parseDate_invalidThrows() {
        assertThrows(BizException.class, () -> DateRangeSupport.parseDate("2026/09/10", "日期起"));
        assertThrows(BizException.class, () -> DateRangeSupport.parseDate("2026-09-10 10:00:00", "日期起"));
        assertThrows(BizException.class, () -> DateRangeSupport.parseDate("10-09-2026", "日期起"));
    }

    @Test
    void parseDateTime_blankReturnsNull() {
        assertNull(DateRangeSupport.parseDateTimeStart(null, "开始时间"));
        assertNull(DateRangeSupport.parseDateTimeStart("  ", "开始时间"));
    }

    @Test
    void parseDateTime_spaceSeparated() {
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 23, 59, 59),
                DateRangeSupport.parseDateTimeEnd("2026-09-11 23:59:59", "结束时间"));
    }

    @Test
    void parseDateTimeEnd_dateOnlyTreatedAsEndOfDay() {
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 23, 59, 59),
                DateRangeSupport.parseDateTimeEnd("2026-09-11", "结束时间"));
    }

    @Test
    void parseDateTime_dateOnlyTreatedAsStartOfDay() {
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 0, 0, 0),
                DateRangeSupport.parseDateTimeStart("2026-09-11", "开始时间"));
    }

    @Test
    void parseDateTime_isoTAndZLegacy() {
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 0, 0, 0),
                DateRangeSupport.parseDateTimeStart("2026-09-11T00:00:00", "开始时间"));
        // 带 Z 的 UTC 写法(历史客户端兼容):按 UTC 直接映射本地字段
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 0, 0, 0),
                DateRangeSupport.parseDateTimeStart("2026-09-11T00:00:00Z", "开始时间"));
    }

    @Test
    void parseDateTime_invalidThrows() {
        assertThrows(BizException.class, () -> DateRangeSupport.parseDateTimeStart("2026/09/11 00:00:00", "开始时间"));
        assertThrows(BizException.class, () -> DateRangeSupport.parseDateTimeStart("not-a-date", "开始时间"));
    }
}
