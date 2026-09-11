package com.company.inventory.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置:时间字段统一序列化格式(一期收尾,用户拍板)。
 *
 * <p>时区约定(重要):库内 timestamp 存的是<b>JVM 本地墙钟</b>(本部署环境时区为
 * 东八区 Asia/Shanghai,已实测验证),展示层即东八区墙钟,<b>无需再做 +8 小时偏移</b>。
 * 注意:早期 Prisma/Fastify 时代写入的存量数据为 UTC,新旧数据存在最多 8 小时
 * 展示偏差,按用户约定不做存量迁移,仅在报告中说明。</p>
 *
 * <p>格式约定:</p>
 * <ul>
 *   <li>LocalDateTime 类字段(createdAt/updatedAt/approvedAt 等)→
 *       {@code yyyy-MM-dd HH:mm:ss}(无 Z 无毫秒,东八区墙钟);</li>
 *   <li>LocalDate 类字段(docDate/expectedDeliveryDate 等)→ {@code yyyy-MM-dd}。</li>
 * </ul>
 *
 * <p>反序列化保持 Spring Boot 默认(ISO 格式),前端日期控件均以
 * {@code YYYY-MM-DD} 字符串提交,兼容不变。</p>
 *
 * @author inventory
 */
@Configuration
public class JacksonConfig {

    /** 东八区时区(展示约定)。 */
    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    /** 时间字段格式:yyyy-MM-dd HH:mm:ss。 */
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 日期字段格式:yyyy-MM-dd。 */
    private static final DateTimeFormatter D_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 注册日期序列化器模块。
     *
     * @return Jackson 模块
     */
    @Bean
    public Module inventoryDateModule() {
        SimpleModule module = new SimpleModule("inventoryDateModule");
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DT_FORMAT.withZone(ZONE_CN)));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(D_FORMAT));
        return module;
    }
}
