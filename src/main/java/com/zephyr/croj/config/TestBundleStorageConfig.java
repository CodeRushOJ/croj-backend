package com.zephyr.croj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.config.properties.TestBundleProperties;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.mapper.TestBundleMapper;
import com.zephyr.croj.problem.S3TestBundleStorage;
import com.zephyr.croj.problem.TestBundleService;
import com.zephyr.croj.problem.TestBundleStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.test-bundle", name = "enabled", havingValue = "true")
public class TestBundleStorageConfig {
    @Bean
    S3Client testBundleS3Client(TestBundleProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build());
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getEndpoint());
        }
        return builder.build();
    }

    @Bean
    TestBundleStorage testBundleStorage(S3Client testBundleS3Client, TestBundleProperties properties) {
        return new S3TestBundleStorage(testBundleS3Client, properties);
    }

    @Bean
    TestBundleService testBundleService(
            TestBundleMapper bundles,
            ProblemVersionMapper versions,
            TestBundleStorage storage,
            ObjectMapper objectMapper,
            TestBundleProperties properties) {
        return new TestBundleService(bundles, versions, storage, objectMapper, properties);
    }
}

