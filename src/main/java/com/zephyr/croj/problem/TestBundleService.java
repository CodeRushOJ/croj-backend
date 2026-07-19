package com.zephyr.croj.problem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.config.properties.TestBundleProperties;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.mapper.TestBundleMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.entity.TestBundle;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class TestBundleService {
    private final TestBundleMapper bundles;
    private final ProblemVersionMapper versions;
    private final TestBundleStorage storage;
    private final ObjectMapper objectMapper;
    private final TestBundleProperties properties;

    @Transactional
    public TestBundle attach(long problemId, long versionId, byte[] archive, String manifestJson) {
        ProblemVersion version = versions.selectById(versionId);
        if (version == null
                || !Long.valueOf(problemId).equals(version.getProblemId())
                || !"DRAFT".equals(version.getState())) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
        }
        if (archive == null || archive.length == 0 || archive.length > properties.getMaxArchiveBytes()) {
            throw invalidBundle("test bundle archive size is invalid");
        }
        String canonicalManifest = validateManifest(manifestJson);
        validateArchive(archive, canonicalManifest);
        String sha256 = sha256(archive);
        String objectKey = "test-bundles/%d/%d/%s.zip".formatted(problemId, versionId, sha256);

        TestBundle existing = bundles.findByProblemVersionId(versionId);
        if (existing != null) {
            if (sha256.equals(existing.getSha256()) && objectKey.equals(existing.getObjectKey())) {
                return existing;
            }
            throw invalidBundle("problem version already has a different test bundle");
        }

        storage.put(objectKey, archive, sha256);
        TestBundle bundle = new TestBundle();
        bundle.setProblemVersionId(versionId);
        bundle.setObjectKey(objectKey);
        bundle.setSha256(sha256);
        bundle.setSizeBytes((long) archive.length);
        bundle.setManifestJson(canonicalManifest);
        bundle.setCreatedAt(LocalDateTime.now());
        if (bundles.insert(bundle) != 1) {
            throw new BusinessException(ResultCodeEnum.CREATE_ERROR);
        }
        return bundle;
    }

    private String validateManifest(String manifestJson) {
        try {
            JsonNode root = objectMapper.readTree(manifestJson);
            JsonNode cases = root == null ? null : root.get("cases");
            if (root == null || !root.isObject() || cases == null || !cases.isArray()
                    || cases.isEmpty() || cases.size() > properties.getMaxCases()) {
                throw invalidBundle("test bundle manifest cases are invalid");
            }
            long calculatedBytes = 0;
            Set<Integer> ids = new HashSet<>();
            Set<String> paths = new HashSet<>();
            for (JsonNode testCase : cases) {
                int id = requiredPositiveInt(testCase, "id");
                if (!ids.add(id)) {
                    throw invalidBundle("test case ids must be unique");
                }
                String input = safeCasePath(testCase, "input");
                String output = safeCasePath(testCase, "output");
                if (!paths.add(input) || !paths.add(output)) {
                    throw invalidBundle("test case paths must be unique");
                }
                calculatedBytes = Math.addExact(calculatedBytes, requiredNonNegativeLong(testCase, "inputBytes"));
                calculatedBytes = Math.addExact(calculatedBytes, requiredNonNegativeLong(testCase, "outputBytes"));
            }
            JsonNode declared = root.get("totalUncompressedBytes");
            if (declared == null || !declared.isIntegralNumber() || !declared.canConvertToLong()
                    || declared.longValue() != calculatedBytes
                    || calculatedBytes > properties.getMaxUncompressedBytes()) {
                throw invalidBundle("test bundle uncompressed size is invalid");
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException | ArithmeticException exception) {
            throw invalidBundle("test bundle manifest is invalid");
        }
    }

    private int requiredPositiveInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw invalidBundle("test case " + field + " is invalid");
        }
        return value.intValue();
    }

    private long requiredNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw invalidBundle("test case " + field + " is invalid");
        }
        return value.longValue();
    }

    private String safeCasePath(JsonNode node, String field) {
        JsonNode value = node.get(field);
        String path = value == null ? null : value.textValue();
        if (path == null
                || !path.startsWith("cases/")
                || path.startsWith("/")
                || path.contains("\\")
                || path.indexOf('\0') >= 0
                || path.contains("//")
                || java.util.Arrays.asList(path.split("/")).contains("..")) {
            throw invalidBundle("test case " + field + " path is unsafe");
        }
        return path;
    }

    private void validateArchive(byte[] archive, String canonicalManifest) {
        if (archive.length < 4
                || archive[0] != 'P'
                || archive[1] != 'K'
                || archive[2] != 3
                || archive[3] != 4) {
            throw invalidBundle("test bundle is not a ZIP archive");
        }
        Map<String, Long> expectedBytes = expectedArchiveEntries(canonicalManifest);
        Set<String> names = new HashSet<>();
        long totalBytes = 0;
        long maximumByRatio = Math.multiplyExact(
                (long) archive.length,
                properties.getMaxCompressionRatio());
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                String name = entry.getName();
                if (entry.isDirectory() || !safeArchivePath(name) || !names.add(name)) {
                    throw invalidBundle("test bundle contains an unsafe or duplicate ZIP entry");
                }
                Long expected = expectedBytes.remove(name);
                if (expected == null) {
                    throw invalidBundle("test bundle contains an undeclared ZIP entry");
                }
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes = Math.addExact(entryBytes, read);
                    totalBytes = Math.addExact(totalBytes, read);
                    if (entryBytes > expected
                            || totalBytes > properties.getMaxUncompressedBytes()
                            || totalBytes > maximumByRatio) {
                        throw invalidBundle("test bundle ZIP contents exceed declared limits");
                    }
                }
                if (entryBytes != expected) {
                    throw invalidBundle("test bundle ZIP entry size does not match the manifest");
                }
                zip.closeEntry();
            }
        } catch (IOException | ArithmeticException exception) {
            throw invalidBundle("test bundle ZIP archive is invalid");
        }
        if (entries == 0 || !expectedBytes.isEmpty()) {
            throw invalidBundle("test bundle ZIP entries do not match the manifest");
        }
    }

    private Map<String, Long> expectedArchiveEntries(String canonicalManifest) {
        try {
            Map<String, Long> expected = new HashMap<>();
            for (JsonNode testCase : objectMapper.readTree(canonicalManifest).get("cases")) {
                expected.put(testCase.get("input").textValue(), testCase.get("inputBytes").longValue());
                expected.put(testCase.get("output").textValue(), testCase.get("outputBytes").longValue());
            }
            return expected;
        } catch (JsonProcessingException exception) {
            throw invalidBundle("test bundle manifest is invalid");
        }
    }

    private boolean safeArchivePath(String path) {
        if (path == null
                || path.isBlank()
                || path.startsWith("/")
                || path.contains("\\")
                || path.indexOf('\0') >= 0
                || path.contains("//")) {
            return false;
        }
        return java.util.Arrays.stream(path.split("/"))
                .noneMatch(segment -> segment.equals(".") || segment.equals("..") || segment.isBlank());
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BusinessException invalidBundle(String message) {
        return new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }
}
