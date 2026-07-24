package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zephyr.croj.config.properties.TestBundleProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3TestBundleStorageTest {
    @Test
    void uploadsToThePrivateConfiguredBucketWithIntegrityMetadata() {
        S3Client s3 = mock(S3Client.class);
        TestBundleProperties properties = new TestBundleProperties();
        properties.setBucket("hidden-tests");
        S3TestBundleStorage storage = new S3TestBundleStorage(s3, properties);
        byte[] archive = new byte[] {1, 2, 3};

        storage.put("test-bundles/42/101/a.zip", archive, "abc123");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertEquals("hidden-tests", request.getValue().bucket());
        assertEquals("test-bundles/42/101/a.zip", request.getValue().key());
        assertEquals(3L, request.getValue().contentLength());
        assertEquals("application/zip", request.getValue().contentType());
        assertEquals("abc123", request.getValue().metadata().get("sha256"));
        assertNull(request.getValue().acl());
    }
}
