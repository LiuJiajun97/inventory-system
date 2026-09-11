package com.company.inventory.config;






import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 配置:swagger-ui 挂 /docs,与 Fastify 版一致。
 *
 * @author inventory
 */
@Configuration
public class SwaggerConfig {

    /** Bearer 认证方案名。 */
    private static final String BEARER_AUTH = "bearerAuth";

    /**
     * OpenAPI 文档元信息(标题/描述与 Fastify 版一致,中文 tag 由各 Controller @Tag 声明)。
     *
     * @return OpenAPI 实例
     */
    @Bean
    public OpenAPI inventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("库存管理系统 API")
                        .description("前后端分离的库存管理系统后端接口,中文描述。")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
