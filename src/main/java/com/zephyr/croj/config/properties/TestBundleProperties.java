package com.zephyr.croj.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.test-bundle")
public class TestBundleProperties {
    private boolean enabled;
    @NotBlank private String bucket = "coderushoj-test-bundles";
    @NotBlank private String region = "us-east-1";
    private URI endpoint;
    private boolean pathStyle = true;
    @Positive private long maxArchiveBytes = 256L * 1024 * 1024;
    @Positive private long maxUncompressedBytes = 1024L * 1024 * 1024;
    @Positive private int maxCases = 10_000;
}
