package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;

class TestBundleArchiveWriterTest {
    @Test
    void writesManifestFirstThenSortedFilesWithFixedMetadata() throws Exception {
        String manifest = """
                {"schemaVersion":2,"judgeMode":"ACM","checker":"exact",
                 "limits":{"timeLimitMillis":1000,"memoryLimitMiB":64},
                 "cases":[
                  {"id":"2","input":"cases/2.in","output":"cases/2.out","weight":1},
                  {"id":"1","input":"cases/1.in","output":"cases/1.out","weight":1}
                 ]}
                """;
        Map<String, byte[]> reversed = new LinkedHashMap<>();
        reversed.put("cases/2.out", "two".getBytes(StandardCharsets.UTF_8));
        reversed.put("cases/2.in", "2".getBytes(StandardCharsets.UTF_8));
        reversed.put("cases/1.out", "one".getBytes(StandardCharsets.UTF_8));
        reversed.put("cases/1.in", "1".getBytes(StandardCharsets.UTF_8));

        byte[] first = new TestBundleArchiveWriter().write(manifest, reversed);
        byte[] second = new TestBundleArchiveWriter().write(
                manifest,
                Map.of(
                        "cases/1.in", "1".getBytes(StandardCharsets.UTF_8),
                        "cases/1.out", "one".getBytes(StandardCharsets.UTF_8),
                        "cases/2.in", "2".getBytes(StandardCharsets.UTF_8),
                        "cases/2.out", "two".getBytes(StandardCharsets.UTF_8)));

        assertArrayEquals(first, second);
        try (ZipFile zip = ZipFile.builder()
                .setSeekableByteChannel(new SeekableInMemoryByteChannel(first))
                .get()) {
            List<ZipArchiveEntry> entries = new ArrayList<>();
            zip.getEntries().asIterator().forEachRemaining(entries::add);
            assertEquals(
                    List.of(
                            "manifest.json",
                            "cases/1.in",
                            "cases/1.out",
                            "cases/2.in",
                            "cases/2.out"),
                    entries.stream().map(ZipArchiveEntry::getName).toList());
            for (ZipArchiveEntry entry : entries) {
                assertEquals(ZipArchiveEntry.DEFLATED, entry.getMethod(), entry.getName());
                assertEquals(
                        UnixStat.FILE_FLAG | 0600,
                        entry.getUnixMode(),
                        entry.getName());
                assertEquals(
                        Instant.parse("1980-01-01T00:00:00Z"),
                        entry.getLastModifiedTime().toInstant(),
                        entry.getName());
            }
        }
    }

    @Test
    void highlyCompressibleCasesStayWithinTheAttachCompressionRatioEnvelope() throws Exception {
        String manifest = """
                {"schemaVersion":1,"judgeMode":"ACM","checker":"exact",
                 "limits":{"timeLimitMillis":1000,"memoryLimitMiB":64},
                 "cases":[
                  {"id":"1","input":"1.in","output":"1.out","weight":1}
                 ]}
                """;
        byte[] repeated = "0".repeat(32_000).getBytes(StandardCharsets.UTF_8);

        byte[] archive = new TestBundleArchiveWriter().write(
                manifest, Map.of("1.in", repeated, "1.out", repeated));

        try (ZipFile zip = ZipFile.builder()
                .setSeekableByteChannel(new SeekableInMemoryByteChannel(archive))
                .get()) {
            for (String name : List.of("1.in", "1.out")) {
                ZipArchiveEntry entry = zip.getEntry(name);
                long ratio = entry.getSize() / Math.max(1, entry.getCompressedSize());
                assertEquals(true, ratio <= 200, name);
            }
        }
    }
}
