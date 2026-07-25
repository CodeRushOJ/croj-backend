package com.zephyr.croj.config.properties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration expiration, String header, String tokenPrefix) {

    public JwtProperties {
        secret = requireText(secret, "jwt.secret");
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret must contain at least 32 bytes");
        }
        expiration = Objects.requireNonNull(expiration, "jwt.expiration is required");
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("jwt.expiration must be positive");
        }
        header = requireText(header, "jwt.header");
        tokenPrefix = requireText(tokenPrefix, "jwt.token-prefix");
    }

    private static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " is required");
        }
        return value.trim();
    }
}
