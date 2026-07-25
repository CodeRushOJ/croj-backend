package com.zephyr.croj.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zephyr.croj.config.properties.TestBundleProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.services.s3.S3Client;

class TestBundleStorageConfigTest {
    @Test
    void limitsOptionalChecksumsForS3CompatibleObjectStores() {
        TestBundleProperties properties = new TestBundleProperties();

        try (S3Client client = new TestBundleStorageConfig().testBundleS3Client(properties)) {
            assertThat(client.serviceClientConfiguration().requestChecksumCalculation())
                    .isEqualTo(RequestChecksumCalculation.WHEN_REQUIRED);
            assertThat(client.serviceClientConfiguration().responseChecksumValidation())
                    .isEqualTo(ResponseChecksumValidation.WHEN_REQUIRED);
        }
    }
}
