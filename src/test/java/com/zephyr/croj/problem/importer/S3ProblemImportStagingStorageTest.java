package com.zephyr.croj.problem.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zephyr.croj.config.properties.TestBundleProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ProblemImportStagingStorageTest {
    @Test
    void stagesAndReadsPrivatePackagesUsingOpaqueObjectKeys() {
        S3Client s3 = mock(S3Client.class);
        TestBundleProperties properties = new TestBundleProperties();
        properties.setBucket("hidden-tests");
        S3ProblemImportStagingStorage storage = new S3ProblemImportStagingStorage(s3, properties);
        byte[] bytes = new byte[] {1, 2, 3};
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));

        String key = storage.put("job-42", bytes, "a".repeat(64));
        byte[] restored = storage.get(key);

        assertThat(key).isEqualTo("problem-import-staging/job-42/" + "a".repeat(64) + ".package");
        assertThat(restored).containsExactly(bytes);
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("hidden-tests");
        assertThat(put.getValue().contentType()).isEqualTo("application/octet-stream");
    }
}
