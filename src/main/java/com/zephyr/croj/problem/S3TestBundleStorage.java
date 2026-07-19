package com.zephyr.croj.problem;

import com.zephyr.croj.config.properties.TestBundleProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RequiredArgsConstructor
public class S3TestBundleStorage implements TestBundleStorage {
    private final S3Client s3;
    private final TestBundleProperties properties;

    @Override
    public void put(String objectKey, byte[] archive, String sha256) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentLength((long) archive.length)
                .contentType("application/zip")
                .metadata(Map.of("sha256", sha256))
                .build();
        s3.putObject(request, RequestBody.fromBytes(archive));
    }
}
