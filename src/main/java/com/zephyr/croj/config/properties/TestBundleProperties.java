package com.zephyr.croj.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.test-bundle")
public class TestBundleProperties {
    public static final long V1_MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    public static final long V1_MAX_MANIFEST_BYTES = 1024L * 1024;
    public static final long V1_MAX_BUNDLE_BYTES = 63L * 1024 * 1024;
    public static final int V1_MAX_CASES = 256;
    public static final int V1_MAX_COMPRESSION_RATIO = 200;

    private boolean enabled;
    @NotBlank private String bucket = "coderushoj-test-bundles";
    @NotBlank private String region = "us-east-1";
    private URI endpoint;
    private boolean pathStyle = true;
    @Positive
    @Max(V1_MAX_ARCHIVE_BYTES)
    private long maxArchiveBytes = V1_MAX_ARCHIVE_BYTES;
    @Positive
    @Max(V1_MAX_MANIFEST_BYTES)
    private long maxManifestBytes = V1_MAX_MANIFEST_BYTES;

    @Positive
    @Max(V1_MAX_BUNDLE_BYTES)
    private long maxCaseBytes = V1_MAX_BUNDLE_BYTES;

    @Positive
    @Max(V1_MAX_BUNDLE_BYTES)
    private long maxUncompressedBytes = V1_MAX_BUNDLE_BYTES;

    @Positive
    @Max(V1_MAX_CASES)
    private int maxCases = V1_MAX_CASES;

    @Positive
    @Max(V1_MAX_COMPRESSION_RATIO)
    private int maxCompressionRatio = V1_MAX_COMPRESSION_RATIO;
}
