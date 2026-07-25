package com.zephyr.croj.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(List<String> allowedOrigins, boolean allowCredentials) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.security.cors.allowed-origins is required");
        }
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("credentialed CORS must not use a wildcard origin");
        }
    }
}
