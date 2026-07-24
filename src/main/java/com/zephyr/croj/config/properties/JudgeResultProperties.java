package com.zephyr.croj.config.properties;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.judge-result")
public record JudgeResultProperties(String serviceToken) {
    public JudgeResultProperties {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException("app.judge-result.service-token is required");
        }
        serviceToken = serviceToken.trim();
        if (serviceToken.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.judge-result.service-token must contain at least 32 bytes");
        }
    }
}
