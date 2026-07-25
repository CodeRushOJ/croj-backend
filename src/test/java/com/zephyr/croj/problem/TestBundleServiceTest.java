package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jakarta.validation.Validation;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
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
        properties.setMaxArchiveBytes(64 * 1024);
        properties.setMaxUncompressedBytes(64 * 1024);
        properties.setMaxCases(10);
        service = new TestBundleService(bundles, versions, storage, new ObjectMapper(), properties);
    }

    @Test
    void attachesAContentAddressedPrivateBundleToADraftVersion() throws Exception {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        version.setProjectionComplete(true);
        version.setStatementJson(completeStatement());
        version.setLimitsJson("{\"timeLimit\":1000,\"memoryLimit\":64,\"totalScore\":100}");
        version.setJudgeConfigJson(
                completeJudge(0, false, "exact"));
        when(versions.selectById(101L)).thenReturn(version);
        when(bundles.insert(any(TestBundle.class))).thenAnswer(invocation -> {
            invocation.<TestBundle>getArgument(0).setId(7L);
            return 1;
        });
        String manifest = validManifest();
        byte[] archive = bundleZip(
                manifest,
                Map.of("cases/1.in", "in", "cases/1.out", "ok"));

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
        assertEquals(
                new ObjectMapper().readTree(manifest),
                new ObjectMapper().readTree(inserted.getValue().getManifestJson()));
    }

    @Test
    void rejectsUnsafeOrInflatedManifestBeforeTouchingObjectStorage() {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        version.setProjectionComplete(true);
        version.setStatementJson(completeStatement());
        version.setLimitsJson("{\"timeLimit\":1000,\"memoryLimit\":64,\"totalScore\":100}");
        version.setJudgeConfigJson(
                completeJudge(0, false, "exact"));
        when(versions.selectById(101L)).thenReturn(version);
        String manifest = """
                {"schemaVersion":1,"judgeMode":"ACM","checker":"exact","cases":[
                  {"id":"1","input":"../secret","output":"cases/1.out","weight":1}
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
                validManifest()));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsArchiveEntriesThatAreUndeclaredOrHaveDifferentActualSizes() {
        draftVersion();
        String manifest = validManifest();
        byte[] undeclared = bundleZip(manifest, Map.of(
                "cases/1.in", "in",
                "cases/1.out", "ok",
                "cases/secret.txt", "hidden"));
        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, undeclared, manifest));

        byte[] missing = bundleZip(manifest, Map.of("cases/1.in", "in"));
        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, missing, manifest));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsTraversalEntriesBeforeObjectStorage() {
        draftVersion();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", validManifest());
        entries.put("cases/1.in", "in");
        entries.put("cases/1.out", "ok");
        entries.put("cases/../escape", "bad");

        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, zip(entries), validManifest()));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsAnEmbeddedManifestThatDisagreesWithDatabaseMetadata() {
        draftVersion();
        String supplied = validManifest();
        String embedded = supplied.replace("\"checker\":\"exact\"", "\"checker\":\"token\"");

        assertThrows(BusinessException.class, () -> service.attach(
                42L,
                101L,
                bundleZip(embedded, Map.of("cases/1.in", "in", "cases/1.out", "ok")),
                supplied));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsManifestTypesAndFieldsThatJudgingServerWouldReject() {
        draftVersion();
        String stringVersion = validManifest().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"");
        String unknownField = validManifest().replace(
                "\"checker\":\"exact\"",
                "\"checker\":\"exact\",\"inputBytes\":2");

        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, new byte[] {1, 2, 3, 4}, stringVersion));
        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, new byte[] {1, 2, 3, 4}, unknownField));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsAnEmbeddedManifestThatIsNotValidUtf8() {
        draftVersion();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", new byte[] {(byte) 0xc3, (byte) 0x28});
        entries.put("cases/1.in", new byte[] {'i', 'n'});
        entries.put("cases/1.out", new byte[] {'o', 'k'});

        assertThrows(BusinessException.class, () -> service.attach(
                42L, 101L, zipBytes(entries)));

        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void extractsTheCanonicalManifestFromACompleteBundleUpload() throws Exception {
        draftVersion();
        when(bundles.insert(any(TestBundle.class))).thenReturn(1);
        String manifest = validManifest();
        byte[] archive = bundleZip(
                manifest,
                Map.of("cases/1.in", "in", "cases/1.out", "ok"));

        TestBundle bundle = service.attach(42L, 101L, archive);

        assertEquals(
                new ObjectMapper().readTree(manifest),
                new ObjectMapper().readTree(bundle.getManifestJson()));
    }

    @Test
    void rejectsOiProblemVersionsBeforeObjectStorage() {
        draftVersion(1, false, "exact");
        String manifest = validManifest();
        byte[] archive = bundleZip(
                manifest,
                Map.of("cases/1.in", "in", "cases/1.out", "ok"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, archive, manifest));

        assertEquals("problem version is incompatible with TestBundle v1", exception.getMessage());
        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsSpecialJudgeProblemVersionsBeforeObjectStorage() {
        draftVersion(0, true, "exact");
        String manifest = validManifest();
        byte[] archive = bundleZip(
                manifest,
                Map.of("cases/1.in", "in", "cases/1.out", "ok"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, archive, manifest));

        assertEquals("problem version is incompatible with TestBundle v1", exception.getMessage());
        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsAManifestWhoseCheckerDiffersFromTheProblemVersion() {
        draftVersion(0, false, "token");
        String manifest = validManifest();
        byte[] archive = bundleZip(
                manifest,
                Map.of("cases/1.in", "in", "cases/1.out", "ok"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, archive, manifest));

        assertEquals("problem version is incompatible with TestBundle v1", exception.getMessage());
        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsLegacyVersionsWhosePublicProjectionIsIncomplete() throws Exception {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        version.setProjectionComplete(true);
        version.setStatementJson(completeStatement());
        version.setLimitsJson("{\"timeLimit\":1000,\"memoryLimit\":64,\"totalScore\":100}");
        version.setJudgeConfigJson(
                completeJudge(0, false, "exact"));
        assertDoesNotThrow(() -> ProblemVersion.class
                        .getMethod("setProjectionComplete", Boolean.class))
                .invoke(version, false);
        when(versions.selectById(101L)).thenReturn(version);
        String manifest = validManifest();
        byte[] archive = bundleZip(
                manifest,
                Map.of("cases/1.in", "in", "cases/1.out", "ok"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, archive, manifest));

        assertEquals("problem version is incompatible with TestBundle v1", exception.getMessage());
        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void defaultLimitsFitTheJudgingBatchExecutionEnvelope() {
        TestBundleProperties defaults = new TestBundleProperties();

        assertEquals(256, defaults.getMaxCases());
        assertEquals(63L * 1024 * 1024, defaults.getMaxCaseBytes());
        assertEquals(63L * 1024 * 1024, defaults.getMaxUncompressedBytes());
        assertEquals(200, defaults.getMaxCompressionRatio());
    }

    @Test
    void configurationCannotWidenTheImmutableV1ExecutionEnvelope() {
        TestBundleProperties widened = new TestBundleProperties();
        widened.setMaxArchiveBytes((256L * 1024 * 1024) + 1);
        widened.setMaxManifestBytes((1024L * 1024) + 1);
        widened.setMaxCaseBytes((63L * 1024 * 1024) + 1);
        widened.setMaxUncompressedBytes((63L * 1024 * 1024) + 1);
        widened.setMaxCases(257);
        widened.setMaxCompressionRatio(201);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var paths = factory.getValidator().validate(widened).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(java.util.stream.Collectors.toSet());
            org.junit.jupiter.api.Assertions.assertEquals(
                    Set.of(
                            "maxArchiveBytes",
                            "maxManifestBytes",
                            "maxCaseBytes",
                            "maxUncompressedBytes",
                            "maxCases",
                            "maxCompressionRatio"),
                    paths);
        }
    }

    @Test
    void rejectsMoreCasesThanTheJudgingBatchCanExecute() {
        TestBundleProperties defaults = new TestBundleProperties();
        TestBundleService defaultLimitedService =
                new TestBundleService(bundles, versions, storage, new ObjectMapper(), defaults);
        draftVersion();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> defaultLimitedService.attach(
                        42L,
                        101L,
                        new byte[] {'P', 'K', 3, 4},
                        manifestWithCaseCount(257)));

        assertEquals("test bundle manifest cases are invalid", exception.getMessage());
        verifyNoInteractions(storage);
        verifyNoInteractions(bundles);
    }

    @Test
    void rejectsCaseFilesThatAreNotStrictUtf8() {
        draftVersion();
        when(bundles.insert(any(TestBundle.class))).thenReturn(1);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", validManifest().getBytes(StandardCharsets.UTF_8));
        entries.put("cases/1.in", new byte[] {(byte) 0xc3, (byte) 0x28});
        entries.put("cases/1.out", "ok".getBytes(StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, zipBytes(entries), validManifest()));

        assertEquals("test case files must be valid UTF-8", exception.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsAnArchiveWhoseCentralDirectoryIsTruncated() {
        draftVersion();
        when(bundles.insert(any(TestBundle.class))).thenReturn(1);
        byte[] complete = bundleZip(
                validManifest(),
                Map.of("cases/1.in", "in", "cases/1.out", "ok"));
        byte[] truncated = Arrays.copyOf(complete, complete.length - 22);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, truncated, validManifest()));

        assertEquals("test bundle ZIP archive is invalid", exception.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnixSymlinkEntries() {
        draftVersion();
        when(bundles.insert(any(TestBundle.class))).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, symlinkBundleZip(), validManifest()));

        assertEquals("test bundle contains a non-regular ZIP entry", exception.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    void appliesCompressionRatioToEachEntryInsteadOfTheWholeArchive() {
        draftVersion();
        when(bundles.insert(any(TestBundle.class))).thenReturn(1);
        byte[] compressibleInput = new byte[10_000];
        byte[] incompressibleOutput = new byte[20_000];
        new Random(42L).nextBytes(incompressibleOutput);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", validManifest().getBytes(StandardCharsets.UTF_8));
        entries.put("cases/1.in", compressibleInput);
        entries.put("cases/1.out", incompressibleOutput);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.attach(42L, 101L, zipBytes(entries), validManifest()));

        assertEquals("test bundle ZIP entry exceeds the compression ratio", exception.getMessage());
        verifyNoInteractions(storage);
    }

    private void draftVersion() {
        draftVersion(0, false, "exact");
    }

    private void draftVersion(int judgeMode, boolean specialJudge, String checker) {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        version.setProjectionComplete(true);
        version.setStatementJson(completeStatement());
        version.setLimitsJson("{\"timeLimit\":1000,\"memoryLimit\":64,\"totalScore\":100}");
        version.setJudgeConfigJson(completeJudge(judgeMode, specialJudge, checker));
        when(versions.selectById(101L)).thenReturn(version);
    }

    private String completeStatement() {
        return """
                {"title":"A","description":"D","inputDescription":"I",
                 "outputDescription":"O","hints":[],"samples":[],"source":null,"tags":[]}
                """;
    }

    private String completeJudge(int judgeMode, boolean specialJudge, String checker) {
        return """
                {"judgeMode":%d,"specialJudge":%s,"specialJudgeCode":null,
                 "specialJudgeLanguage":null,"checker":"%s","difficulty":2}
                """.formatted(judgeMode, specialJudge, checker);
    }

    private String validManifest() {
        return """
                {"schemaVersion":1,"judgeMode":"ACM","checker":"exact",
                 "limits":{"timeLimitMillis":1000,"memoryLimitMiB":64},"cases":[
                  {"id":"1","input":"cases/1.in","output":"cases/1.out","weight":1}
                ]}
                """;
    }

    private String manifestWithCaseCount(int count) {
        StringBuilder cases = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            if (index > 1) {
                cases.append(',');
            }
            cases.append("""
                    {"id":"%1$d","input":"cases/%1$d.in","output":"cases/%1$d.out","weight":1}
                    """.formatted(index).trim());
        }
        return """
                {"schemaVersion":1,"judgeMode":"ACM","checker":"exact",
                 "limits":{"timeLimitMillis":1000,"memoryLimitMiB":64},"cases":[%s]}
                """.formatted(cases);
    }

    private byte[] bundleZip(String manifest, Map<String, String> cases) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifest);
        entries.putAll(cases);
        return zip(entries);
    }

    private byte[] zip(Map<String, String> entries) {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        entries.forEach((name, content) ->
                bytes.put(name, content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return zipBytes(bytes);
    }

    private byte[] zipBytes(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    ZipEntry item = new ZipEntry(entry.getKey());
                    item.setTime(0L);
                    zip.putNextEntry(item);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private byte[] symlinkBundleZip() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
                addCommonsEntry(
                        zip,
                        "manifest.json",
                        validManifest().getBytes(StandardCharsets.UTF_8),
                        UnixStat.FILE_FLAG | 0644);
                addCommonsEntry(
                        zip,
                        "cases/1.in",
                        "target".getBytes(StandardCharsets.UTF_8),
                        UnixStat.LINK_FLAG | 0777);
                addCommonsEntry(
                        zip,
                        "cases/1.out",
                        "ok".getBytes(StandardCharsets.UTF_8),
                        UnixStat.FILE_FLAG | 0644);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private void addCommonsEntry(
            ZipArchiveOutputStream zip, String name, byte[] contents, int unixMode)
            throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setUnixMode(unixMode);
        entry.setTime(0L);
        zip.putArchiveEntry(entry);
        zip.write(contents);
        zip.closeArchiveEntry();
    }
}
