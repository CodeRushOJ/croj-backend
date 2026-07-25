package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.config.properties.TestBundleProperties;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.mapper.TestBundleMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.entity.TestBundle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TestBundleServiceTest {
    private final TestBundleMapper bundles = mock(TestBundleMapper.class);
    private final ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
    private final TestBundleStorage storage = mock(TestBundleStorage.class);
    private TestBundleService service;

    @BeforeEach
    void setUp() {
        TestBundleProperties properties = new TestBundleProperties();
        properties.setMaxArchiveBytes(1024);
        properties.setMaxUncompressedBytes(4096);
        properties.setMaxCases(10);
        service = new TestBundleService(bundles, versions, storage, new ObjectMapper(), properties);
    }

    @Test
    void attachesAContentAddressedPrivateBundleToADraftVersion() {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        when(versions.selectById(101L)).thenReturn(version);
        when(bundles.insert(any(TestBundle.class))).thenAnswer(invocation -> {
            invocation.<TestBundle>getArgument(0).setId(7L);
            return 1;
        });
        byte[] archive = zip(Map.of("cases/1.in", "in", "cases/1.out", "ok"));
        String manifest = """
                {"totalUncompressedBytes":4,"cases":[
                  {"id":1,"input":"cases/1.in","output":"cases/1.out","inputBytes":2,"outputBytes":2}
                ]}
                """;

        TestBundle result = service.attach(42L, 101L, archive, manifest);

        assertEquals(7L, result.getId());
        assertEquals(archive.length, result.getSizeBytes());
        assertEquals(64, result.getSha256().length());
        assertEquals(
                "test-bundles/42/101/" + result.getSha256() + ".zip",
                result.getObjectKey());
        verify(storage).put(result.getObjectKey(), archive, result.getSha256());
        ArgumentCaptor<TestBundle> inserted = ArgumentCaptor.forClass(TestBundle.class);
        verify(bundles).insert(inserted.capture());
        assertEquals(101L, inserted.getValue().getProblemVersionId());
    }

    @Test
    void rejectsUnsafeOrInflatedManifestBeforeTouchingObjectStorage() {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        when(versions.selectById(101L)).thenReturn(version);
        String manifest = """
                {"totalUncompressedBytes":5000,"cases":[
                  {"id":1,"input":"../secret","output":"cases/1.out","inputBytes":2500,"outputBytes":2500}
                ]}
                """;

        assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, new byte[] {1}, manifest));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsBytesThatAreNotAZipEvenWhenTheManifestLooksValid() {
        draftVersion();

        assertThrows(BusinessException.class, () -> service.attach(
                42L,
                101L,
                new byte[] {1, 2, 3, 4},
                validManifest(2, 2)));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsArchiveEntriesThatAreUndeclaredOrHaveDifferentActualSizes() {
        draftVersion();
        byte[] undeclared = zip(Map.of(
                "cases/1.in", "in",
                "cases/1.out", "ok",
                "cases/secret.txt", "hidden"));
        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, undeclared, validManifest(2, 2)));

        byte[] wrongSize = zip(Map.of("cases/1.in", "longer", "cases/1.out", "ok"));
        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, wrongSize, validManifest(2, 2)));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsTraversalEntriesBeforeObjectStorage() {
        draftVersion();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("cases/1.in", "in");
        entries.put("cases/1.out", "ok");
        entries.put("cases/../escape", "bad");

        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, zip(entries), validManifest(2, 2)));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    private void draftVersion() {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        when(versions.selectById(101L)).thenReturn(version);
    }

    private String validManifest(long inputBytes, long outputBytes) {
        return """
                {"totalUncompressedBytes":%d,"cases":[
                  {"id":1,"input":"cases/1.in","output":"cases/1.out","inputBytes":%d,"outputBytes":%d}
                ]}
                """.formatted(inputBytes + outputBytes, inputBytes, outputBytes);
    }

    private byte[] zip(Map<String, String> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    ZipEntry item = new ZipEntry(entry.getKey());
                    item.setTime(0L);
                    zip.putNextEntry(item);
                    zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
