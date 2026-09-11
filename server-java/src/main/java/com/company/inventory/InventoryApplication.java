package com.company.inventory;

import java.time.ZoneId;
import java.util.TimeZone;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 库存管理系统 Java 后端启动类。
 *
 * <p>技术栈:Java 21 + Spring Boot 3.5 + MyBatis-Plus + PostgreSQL,
 * API 契约与 Fastify 版逐接口兼容(基准:contract-snapshot.json)。</p>
 *
 * <p>时区约定:数据库存储与接口展示均为东八区墙钟,启动时显式锁定 JVM 默认时区为
 * Asia/Shanghai,不依赖操作系统时区设置。</p>
 *
 * @author inventory
 */
@SpringBootApplication
@MapperScan("com.company.inventory.mapper")
public class InventoryApplication {

    /** 统一时区:数据库存储与接口展示均为东八区墙钟,不依赖操作系统时区。 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_SHANGHAI));
        SpringApplication.run(InventoryApplication.class, args);
    }
}
