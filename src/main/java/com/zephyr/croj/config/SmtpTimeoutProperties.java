package com.zephyr.croj.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.smtp")
public record SmtpTimeoutProperties(
        @Min(100) @Max(60_000) int connectionTimeoutMillis,
        @Min(100) @Max(60_000) int readTimeoutMillis,
        @Min(100) @Max(60_000) int writeTimeoutMillis) {}
