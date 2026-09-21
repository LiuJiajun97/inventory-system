package com.company.inventory.config;







import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置:注册 JWT 拦截器与 CORS(与 Fastify 版一致:任意来源 + 凭证)。
 *
 * @author inventory
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    /** JWT 拦截器。 */
    private final JwtInterceptor jwtInterceptor;

    /** 写接口幂等拦截器。 */
    private final IdempotencyInterceptor idempotencyInterceptor;

    /**
     * 构造配置。
     *
     * @param jwtInterceptor         JWT 拦截器
     * @param idempotencyInterceptor 写接口幂等拦截器
     */
    public WebMvcConfig(JwtInterceptor jwtInterceptor, IdempotencyInterceptor idempotencyInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    /**
     * 注册拦截器:全部 /api/v1/** 需登录(登录接口放行),写接口再叠加幂等拦截。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/login");
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/v1/**");
    }

    /**
     * CORS 配置:与 Fastify 版 @fastify/cors(origin: true, credentials: true) 对齐。
     *
     * @param registry CORS 注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
