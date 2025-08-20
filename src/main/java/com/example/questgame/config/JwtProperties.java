package com.example.questgame.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация JWT (берётся из application.properties: app.jwt.*).
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    /** Секрет для подписи JWT. */
    private String secret = "change_me";
    /** TTL токена в секундах. */
    private long expirationSeconds = 604800;
    /** Включён ли вообще JWT-фильтр. */
    private boolean enabled = true;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getExpirationSeconds() { return expirationSeconds; }
    public void setExpirationSeconds(long expirationSeconds) { this.expirationSeconds = expirationSeconds; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}