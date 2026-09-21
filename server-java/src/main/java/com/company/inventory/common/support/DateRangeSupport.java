package com.company.inventory.common.support;

import com.company.inventory.common.exception.BizException;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 列表查询日期/时间区间参数解析支撑(全项目列表接口日期参数的唯一入口)。
 *
 * <p>统一约定:前端传东八区本地时间的<b>空格分隔</b>格式——
 * 日期列(date)用 {@code yyyy-MM-dd},时间列(timestamp)用 {@code yyyy-MM-dd HH:mm:ss}。
 * 另兼容 ISO 的 {@code T} 分隔与带 {@code Z} 的 UTC 写法(历史客户端)。
 *
 * <p>禁止把字符串直接传给 MyBatis-Plus 的 ge/le:PostgreSQL 对 date/timestamp
 * 与 varchar 无隐式比较运算符,会报 operator does not exist(500)。</p>
 *
 * @author inventory
 */
public final class DateRangeSupport {

    /** 纯日期区间止归一化时点:23。 */
    private static final int END_OF_DAY_HOUR = 23;
    /** 纯日期区间止归一化时点:59。 */
    private static final int END_OF_DAY_MINUTE = 59;
    /** 纯日期区间止归一化时点:59。 */
    private static final int END_OF_DAY_SECOND = 59;

    private DateRangeSupport() {
        // 工具类禁止实例化
    }

    /**
     * 解析日期参数(单据日期列,东八区)。
     *
     * @param value 原始参数(可空,空白视为 null)
     * @param name  参数名(用于错误提示)
     * @return 解析结果(参数为空白时返回 null)
     * @throws BizException 格式非法(应为 yyyy-MM-dd)
     */
    public static LocalDate parseDate(String value, String name) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.length() > 10) {
            throw new BizException(name + " 格式错误,应为 yyyy-MM-dd: " + value);
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new BizException(name + " 格式错误,应为 yyyy-MM-dd: " + value);
        }
    }

    /**
     * 解析区间起时间(流水时间列,东八区)。
     *
     * <p>支持:{@code yyyy-MM-dd HH:mm:ss}(首选)、{@code yyyy-MM-dd}(归一化为当天
     * 00:00:00)、ISO 的 T 分隔、带 Z 的 UTC 写法(历史客户端)。</p>
     *
     * @param value 原始参数(可空,空白视为 null)
     * @param name  参数名(用于错误提示)
     * @return 解析结果(参数为空白时返回 null)
     * @throws BizException 格式非法
     */
    public static LocalDateTime parseDateTimeStart(String value, String name) {
        return parseDateTime(value, name, false);
    }

    /**
     * 解析区间止时间(流水时间列,东八区)。
     *
     * <p>支持格式同 {@link #parseDateTimeStart(String, String)},但纯日期
     * {@code yyyy-MM-dd} 归一化为当天 23:59:59(用户"到某天"通常含当天)。</p>
     *
     * @param value 原始参数(可空,空白视为 null)
     * @param name  参数名(用于错误提示)
     * @return 解析结果(参数为空白时返回 null)
     * @throws BizException 格式非法
     */
    public static LocalDateTime parseDateTimeEnd(String value, String name) {
        return parseDateTime(value, name, true);
    }

    private static LocalDateTime parseDateTime(String value, String name, boolean isEnd) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.endsWith("Z")) {
                return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
            }
            // 纯日期:起视为当天 00:00:00,止视为当天 23:59:59
            if (text.length() == 10) {
                LocalDate day = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                return isEnd ? day.atTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE, END_OF_DAY_SECOND)
                        : day.atStartOfDay();
            }
            // 含时间部分:空格分隔归一化为 ISO 的 T 分隔后再解析(ISO_LOCAL_DATE_TIME 只认 T)
            String normalized = text.replace(' ', 'T');
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BizException(
                    name + " 格式错误,应为 yyyy-MM-dd HH:mm:ss: " + value);
        }
    }
}
