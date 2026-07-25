package com.zephyr.croj.problem.importer;

import com.zephyr.croj.config.properties.TestBundleProperties;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RequiredArgsConstructor
public class S3ProblemImportStagingStorage implements ProblemImportStagingStorage {
    private final S3Client s3;
    private final TestBundleProperties properties;

    @Override
    public String put(String jobId, byte[] packageBytes, String sha256) {
        String key = "problem-import-staging/%s/%s.package".formatted(jobId, sha256);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentLength((long) packageBytes.length)
                        .contentType("application/octet-stream")
                        .metadata(java.util.Map.of("sha256", sha256))
                        .build(),
                RequestBody.fromBytes(packageBytes));
        return key;
    }

    @Override
    public byte[] get(String objectKey) {
        return s3.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build())
                .asByteArray();
    }
}
