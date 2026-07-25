package com.zephyr.croj.problem.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.entity.TestBundle;
import com.zephyr.croj.problem.ProblemVersionPublicationService;
import com.zephyr.croj.problem.TestBundleService;
import com.zephyr.croj.service.ProblemService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProblemImportServiceTest {
    private final ProblemPackageReader reader = mock(ProblemPackageReader.class);
    private final ProblemImportStagingStorage staging = mock(ProblemImportStagingStorage.class);
    private final ProblemImportJobStore jobs = mock(ProblemImportJobStore.class);
    private final ProblemService problems = mock(ProblemService.class);
    private final ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
    private final TestBundleService bundles = mock(TestBundleService.class);
    private final ProblemVersionPublicationService publication = mock(ProblemVersionPublicationService.class);
    private ProblemImportService service;

    @BeforeEach
    void setUp() {
        service = new ProblemImportService(
                reader,
                staging,
                jobs,
                problems,
                versions,
                bundles,
                publication,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-19T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void preflightPersistsAnOwnerBoundJobAndPrivateStagingObject() {
        byte[] upload = "<fps/>".getBytes(StandardCharsets.UTF_8);
        when(reader.read("fps.xml", upload)).thenReturn(batch());
        when(staging.put(any(), eq(upload), any())).thenAnswer(invocation ->
                "problem-import-staging/" + invocation.getArgument(0) + "/package");

        ProblemImportResponses.Preflight response = service.preflight(7L, "fps.xml", upload);

        assertThat(response.jobId()).isNotBlank();
        assertThat(response.detectedFormat()).isEqualTo("FPS_XML");
        assertThat(response.problemCount()).isEqualTo(1);
        assertThat(response.testCaseCount()).isEqualTo(1);
        assertThat(response.errors()).isEmpty();
        verify(jobs).create(any(ProblemImportJob.class));
    }

    @Test
    void preflightFailsClosedForUnsupportedSpecialJudges() {
        byte[] upload = "<fps/>".getBytes(StandardCharsets.UTF_8);
        ProblemImportBatch batch = batch(List.of(new ProblemImportCodeResource(
                ProblemImportCodeKind.SPECIAL_JUDGE,
                "cpp17",
                "int main(){}")));
        when(reader.read("fps.xml", upload)).thenReturn(batch);
        when(staging.put(any(), eq(upload), any())).thenReturn("problem-import-staging/job/package");

        ProblemImportResponses.Preflight response = service.preflight(7L, "fps.xml", upload);

        assertThat(response.errors()).anyMatch(error -> error.contains("not supported"));
        assertThat(response.problems().get(0).status()).isEqualTo("ERROR");
    }

    @Test
    void preflightRejectsMoreCasesThanManifestV1CanPublish() {
        byte[] upload = "<fps/>".getBytes(StandardCharsets.UTF_8);
        List<ProblemImportCase> cases = IntStream.rangeClosed(1, 257)
                .mapToObj(index -> new ProblemImportCase(
                        Integer.toString(index),
                        Integer.toString(index),
                        Integer.toString(index)))
                .toList();
        ProblemImportDraft draft = new ProblemImportDraft(
                "A+B",
                "Add",
                "Two ints",
                "Sum",
                null,
                "FPS",
                1000,
                65536,
                List.of(new ProblemImportCase("1", "1 2", "3")),
                cases,
                List.of(),
                List.of(),
                null,
                null,
                "A+B");
        when(reader.read("fps.xml", upload)).thenReturn(new ProblemImportBatch(
                ProblemPackageFormat.FPS_XML,
                "1.4",
                null,
                List.of(draft),
                List.of()));

        ProblemImportResponses.Preflight response = service.preflight(7L, "fps.xml", upload);

        assertThat(response.errors()).anyMatch(error -> error.contains("at most 256"));
        assertThat(response.problems().get(0).status()).isEqualTo("ERROR");
        verify(staging, never()).put(any(), any(), any());
    }

    @Test
    void caseContentCapacityReservesSpaceForTheManifest() {
        List<ProblemImportCase> cases =
                List.of(new ProblemImportCase("1", "12345", "67890"));

        assertThat(ProblemImportService.caseContentsFit(cases, 10)).isTrue();
        assertThat(ProblemImportService.caseContentsFit(cases, 9)).isFalse();
    }

    @Test
    void committedJobsAreIdempotentAndDoNotRecreateProblems() {
        ProblemImportJob committed = ProblemImportJob.committed(
                "job-42", 7L, "FPS_XML", "a".repeat(64), "staging/key", "{}", 2);
        when(jobs.lockOwned("job-42", 7L, Instant.parse("2026-07-19T08:00:00Z")))
                .thenReturn(committed);

        ProblemImportResponses.Commit response = service.commit(7L, "job-42");

        assertThat(response.importedCount()).isEqualTo(2);
        verify(staging, never()).get(any());
        verify(problems, never()).createProblem(any(), any());
    }

    @Test
    void commitRevalidatesCreatesAttachesAndPublishesInOneRetryableTransaction() throws Exception {
        byte[] upload = "<fps/>".getBytes(StandardCharsets.UTF_8);
        String digest = java.util.HexFormat.of().formatHex(sha256(upload));
        Instant now = Instant.parse("2026-07-19T08:00:00Z");
        ProblemImportJob validated = new ProblemImportJob(
                "job-42", 7L, "VALIDATED", "FPS_XML", digest, "staging/key", "{}",
                0, now, now.plusSeconds(86400), null);
        when(jobs.lockOwned("job-42", 7L, now)).thenReturn(validated);
        when(staging.get("staging/key")).thenReturn(upload);
        when(reader.read("staging/key", upload)).thenReturn(batch());
        when(problems.createProblem(any(), eq(7L))).thenReturn(42L);
        when(versions.findLatestDraftVersionId(42L)).thenReturn(101L);
        when(bundles.attach(eq(42L), eq(101L), any(), any())).thenReturn(new TestBundle());

        ProblemImportResponses.Commit response = service.commit(7L, "job-42");

        assertThat(response.importedCount()).isEqualTo(1);
        ArgumentCaptor<byte[]> archive = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> manifest = ArgumentCaptor.forClass(String.class);
        verify(bundles).attach(eq(42L), eq(101L), archive.capture(), manifest.capture());
        var manifestJson = new ObjectMapper().readTree(manifest.getValue());
        assertThat(manifestJson.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(manifestJson.path("judgeMode").asText()).isEqualTo("ACM");
        assertThat(manifestJson.path("checker").asText()).isEqualTo("exact");
        assertThat(manifestJson.path("cases").get(0).path("id").isTextual()).isTrue();
        assertThat(manifestJson.path("cases").get(0).path("weight").asInt()).isEqualTo(1);
        Map<String, byte[]> entries = unzip(archive.getValue());
        assertThat(entries.keySet())
                .containsExactly("manifest.json", "cases/1.in", "cases/1.out");
        assertThat(zipMethods(archive.getValue()).values())
                .containsOnly(java.util.zip.ZipEntry.DEFLATED);
        Path fixture = Path.of("src/test/resources/contracts/test-bundle-v1");
        var fixtureManifest = new ObjectMapper().readTree(Files.readAllBytes(fixture.resolve("manifest.json")));
        assertThat(new ObjectMapper().readTree(entries.get("manifest.json")))
                .isEqualTo(fixtureManifest)
                .isEqualTo(manifestJson);
        assertThat(new String(entries.get("cases/1.in"), StandardCharsets.UTF_8))
                .isEqualTo(Files.readString(fixture.resolve("cases/1.in")).strip());
        assertThat(new String(entries.get("cases/1.out"), StandardCharsets.UTF_8))
                .isEqualTo(Files.readString(fixture.resolve("cases/1.out")).strip());
        verify(publication).publish(42L, 101L);
        verify(jobs).markCommitted("job-42", 1, now);
    }

    private Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return entries;
    }

    private Map<String, Integer> zipMethods(byte[] archive) throws IOException {
        Map<String, Integer> methods = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                methods.put(entry.getName(), entry.getMethod());
                zip.closeEntry();
            }
        }
        return methods;
    }

    private byte[] sha256(byte[] value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private ProblemImportBatch batch() {
        return batch(List.of());
    }

    private ProblemImportBatch batch(List<ProblemImportCodeResource> codeResources) {
        ProblemImportDraft draft = new ProblemImportDraft(
                "A+B", "Add", "Two ints", "Sum", null, "FPS",
                1000, 65536,
                List.of(new ProblemImportCase("1", "1 2", "3")),
                List.of(new ProblemImportCase("1", "1 2", "3")),
                codeResources, List.of(), null, null, "A+B");
        return new ProblemImportBatch(ProblemPackageFormat.FPS_XML, "1.4", null, List.of(draft), List.of());
    }
}
