package com.company.inventory;

import java.time.ZoneId;
import java.util.TimeZone;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

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

    /** server.port 缺省值(仅当配置未显式指定时用于横幅展示)。 */
    private static final int DEFAULT_PORT = 8081;

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_SHANGHAI));
        SpringApplication.run(InventoryApplication.class, args);
    }

    /**
     * 应用就绪后在控制台打印访问地址横幅(应用地址、API 前缀、Swagger)。
     *
     * @param env Spring 环境(读取实际生效的 server.port)
     * @return 启动完成回调 Bean
     */
    @Bean
    public ApplicationRunner printAccessUrlRunner(Environment env) {
        return (ApplicationArguments arguments) -> {
            Integer port = env.getProperty("server.port", Integer.class, DEFAULT_PORT);
            String base = "http://127.0.0.1:" + port;
            System.out.println();
            System.out.println("====================================================");
            System.out.println("  库存管理系统 后端已启动");
            System.out.println("  应用地址 : " + base);
            System.out.println("  API 地址 : " + base + "/api/v1");
            System.out.println("  Swagger  : " + base + "/docs");
            System.out.println("====================================================");
            System.out.println();
        };
    }
}
