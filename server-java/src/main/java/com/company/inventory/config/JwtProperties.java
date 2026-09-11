package com.company.inventory.config;






import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项(secret 走环境变量 JWT_SECRET,默认开发值;过期 8 小时)。
 *
 * @author inventory
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥(HS256,至少 32 字节)。 */
    private String secret;

    /** 过期秒数,默认 8 小时。 */
    private long expiresSeconds = 28800L;

    /**
     * 获取签名密钥。
     *
     * @return 密钥
     */
    public String getSecret() {
        return secret;
    }

    /**
     * 设置签名密钥。
     *
     * @param secret 密钥
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * 获取过期秒数。
     *
     * @return 过期秒数
     */
    public long getExpiresSeconds() {
        return expiresSeconds;
    }

    /**
     * 设置过期秒数。
     *
     * @param expiresSeconds 过期秒数
     */
    public void setExpiresSeconds(long expiresSeconds) {
        this.expiresSeconds = expiresSeconds;
    }
}
