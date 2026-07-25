package com.zephyr.croj.problem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/** Writes reproducible TestBundle ZIPs using the metadata contract shared with Judging. */
public final class TestBundleArchiveWriter {
    private static final Instant ZIP_EPOCH = Instant.parse("1980-01-01T00:00:00Z");
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public byte[] write(String canonicalManifestJson, Map<String, byte[]> files) {
        if (canonicalManifestJson == null || files == null) {
            throw new IllegalArgumentException("manifest and files are required");
        }
        JsonNode manifestRoot = parseManifest(canonicalManifestJson);
        byte[] manifest = canonicalManifest(manifestRoot);
        Set<String> referenced = referencedFiles(manifestRoot);
        if (!referenced.equals(files.keySet())
                || files.entrySet().stream().anyMatch(entry -> entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "archive files must exactly match manifest references");
        }
        List<String> names = new ArrayList<>(referenced);
        names.sort(String::compareTo);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
                zip.setUseZip64(Zip64Mode.Never);
                zip.setEncoding(StandardCharsets.UTF_8.name());
                zip.setLevel(Deflater.NO_COMPRESSION);
                zip.setCreateUnicodeExtraFields(
                        ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER);
                writeEntry(zip, "manifest.json", manifest);
                for (String name : names) {
                    writeEntry(zip, name, files.get(name));
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot build deterministic TestBundle ZIP", exception);
        }
    }

    private JsonNode parseManifest(String manifest) {
        try {
            JsonNode root = JSON.readTree(manifest);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("manifest object is required");
            }
            return root;
        } catch (IOException exception) {
            throw new IllegalArgumentException("manifest JSON is invalid", exception);
        }
    }

    private byte[] canonicalManifest(JsonNode manifest) {
        try {
            return JSON.writeValueAsBytes(manifest);
        } catch (IOException exception) {
            throw new IllegalArgumentException("manifest JSON is invalid", exception);
        }
    }

    private Set<String> referencedFiles(JsonNode root) {
        JsonNode cases = root.get("cases");
        if (cases == null || !cases.isArray()) {
            throw new IllegalArgumentException("manifest cases are required");
        }
        Set<String> referenced = new HashSet<>();
        for (JsonNode testCase : cases) {
            addReference(referenced, testCase.path("input").textValue());
            addReference(referenced, testCase.path("output").textValue());
        }
        JsonNode specialJudge = root.get("specialJudge");
        if (specialJudge != null) {
            addReference(referenced, specialJudge.path("source").textValue());
        }
        return Set.copyOf(referenced);
    }

    private void addReference(Set<String> referenced, String name) {
        if (name == null
                || name.isBlank()
                || name.getBytes(StandardCharsets.UTF_8).length > 512
                || !StandardCharsets.UTF_8.newEncoder().canEncode(name)
                || name.equals("manifest.json")
                || name.startsWith("/")
                || name.contains("\\")
                || name.contains("//")
                || name.endsWith("/")
                || java.util.Arrays.asList(name.split("/")).contains("..")
                || java.util.Arrays.asList(name.split("/")).contains(".")
                || !referenced.add(name)) {
            throw new IllegalArgumentException("manifest contains an unsafe or duplicate path");
        }
    }

    private void writeEntry(ZipArchiveOutputStream zip, String name, byte[] contents)
            throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setMethod(ZipArchiveEntry.DEFLATED);
        entry.setUnixMode(UnixStat.FILE_FLAG | 0600);
        FileTime timestamp = FileTime.from(ZIP_EPOCH);
        entry.setLastModifiedTime(timestamp);
        entry.setLastAccessTime(timestamp);
        entry.setCreationTime(timestamp);
        zip.putArchiveEntry(entry);
        zip.write(contents);
        zip.closeArchiveEntry();
    }
}
