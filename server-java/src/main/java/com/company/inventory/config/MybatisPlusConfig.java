package com.company.inventory.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.company.inventory.common.constant.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置:注册分页插件(入库/出库/流水分页列表使用)。
 *
 * <p>说明:本项目 PG 库表名/列名为 PascalCase/camelCase(建库时带引号),
 * 实体 DO 已通过 @TableName/@TableField 注解直接写带引号的标识符,
 * 保证 MyBatis-Plus 生成的 SQL 与建库 DDL 完全一致。</p>
 *
 * @author inventory
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器(分页,PostgreSQL 方言)。
     *
     * @return 拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 单页上限,与契约 pageSize 校验一致(常量见 ErrorCode.PAGE_SIZE_MAX)
        pagination.setMaxLimit(ErrorCode.PAGE_SIZE_MAX);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
