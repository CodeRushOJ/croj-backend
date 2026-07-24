package com.zephyr.croj.problem.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.entity.TestBundle;
import com.zephyr.croj.problem.ProblemVersionPublicationService;
import com.zephyr.croj.problem.TestBundleService;
import com.zephyr.croj.service.ProblemService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TestBundleContractExportTest {
    private static final String OUTPUT_PROPERTY = "croj.contract.output";

    @Test
    void exportsTheRealProblemImportBundleForCrossRepositoryVerification() throws Exception {
        ProblemPackageReader reader = mock(ProblemPackageReader.class);
        ProblemImportStagingStorage staging = mock(ProblemImportStagingStorage.class);
        ProblemImportJobStore jobs = mock(ProblemImportJobStore.class);
        ProblemService problems = mock(ProblemService.class);
        ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
        TestBundleService bundles = mock(TestBundleService.class);
        ProblemVersionPublicationService publication = mock(ProblemVersionPublicationService.class);
        Instant now = Instant.parse("2026-07-19T08:00:00Z");
        byte[] upload = "<fps/>".getBytes(StandardCharsets.UTF_8);
        ProblemImportJob validated = new ProblemImportJob(
                "contract-job",
                7L,
                "VALIDATED",
                "FPS_XML",
                sha256(upload),
                "staging/contract",
                "{}",
                0,
                now,
                now.plusSeconds(86400),
                null);
        when(jobs.lockOwned("contract-job", 7L, now)).thenReturn(validated);
        when(staging.get("staging/contract")).thenReturn(upload);
        when(reader.read("staging/contract", upload)).thenReturn(contractBatch());
        when(problems.createProblem(any(), eq(7L))).thenReturn(42L);
        when(versions.findLatestDraftVersionId(42L)).thenReturn(101L);
        when(bundles.attach(eq(42L), eq(101L), any(), any())).thenReturn(new TestBundle());
        ProblemImportService service = new ProblemImportService(
                reader,
                staging,
                jobs,
                problems,
                versions,
                bundles,
                publication,
                new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC));

        ProblemImportResponses.Commit response = service.commit(7L, "contract-job");

        assertThat(response.importedCount()).isEqualTo(1);
        ArgumentCaptor<byte[]> archive = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> manifest = ArgumentCaptor.forClass(String.class);
        verify(bundles).attach(eq(42L), eq(101L), archive.capture(), manifest.capture());
        assertContractBundle(archive.getValue(), manifest.getValue());
        verify(publication).publish(42L, 101L);

        String configuredOutput = System.getProperty(OUTPUT_PROPERTY);
        if (configuredOutput != null && !configuredOutput.isBlank()) {
            Path output = Path.of(configuredOutput);
            assertThat(output).isAbsolute();
            Path parent = output.getParent();
            assertThat(parent).isNotNull();
            assertThat(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)).isTrue();
            Files.write(
                    output,
                    archive.getValue(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            assertThat(Files.size(output)).isEqualTo(archive.getValue().length);
        }
    }

    private void assertContractBundle(byte[] archive, String manifest) throws Exception {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        try (ZipFile zip = ZipFile.builder()
                .setSeekableByteChannel(new SeekableInMemoryByteChannel(archive))
                .setMaxNumberOfDisks(1)
                .get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                assertThat(entry.isDirectory()).isFalse();
                contents.put(entry.getName(), zip.getInputStream(entry).readAllBytes());
            }
        }
        assertThat(new ArrayList<>(contents.keySet()))
                .containsExactly("manifest.json", "cases/1.in", "cases/1.out");
        ObjectMapper mapper = new ObjectMapper();
        var actualManifest = mapper.readTree(contents.get("manifest.json"));
        assertThat(actualManifest).isEqualTo(mapper.readTree(manifest));
        assertThat(actualManifest.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(actualManifest.path("judgeMode").asText()).isEqualTo("ACM");
        assertThat(actualManifest.path("checker").asText()).isEqualTo("exact");
        assertThat(actualManifest.path("limits").path("timeLimitMillis").asInt()).isEqualTo(1000);
        assertThat(actualManifest.path("limits").path("memoryLimitMiB").asInt()).isEqualTo(64);
        assertThat(actualManifest.path("cases").get(0).path("id").asText()).isEqualTo("1");
        assertThat(actualManifest.path("cases").get(0).path("weight").asInt()).isEqualTo(1);
        assertThat(new String(contents.get("cases/1.in"), StandardCharsets.UTF_8)).isEqualTo("1 2");
        assertThat(new String(contents.get("cases/1.out"), StandardCharsets.UTF_8)).isEqualTo("3");
    }

    private ProblemImportBatch contractBatch() {
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
                List.of(new ProblemImportCase("1", "1 2", "3")),
                List.of(),
                List.of(),
                null,
                null,
                "A+B");
        return new ProblemImportBatch(
                ProblemPackageFormat.FPS_XML,
                "1.4",
                null,
                List.of(draft),
                List.of());
    }

    private String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));
    }
}
