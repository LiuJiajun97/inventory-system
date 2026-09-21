package com.company.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项(secret 走环境变量 JWT_SECRET,默认开发值;过期 8 小时)。
 *
 * @author inventory
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥(HS256,至少 32 字节)。 */
    private String secret;

    /** 过期秒数,默认 8 小时。 */
    private long expiresSeconds = 28800L;
}
