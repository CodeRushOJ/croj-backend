package com.zephyr.croj.problem;

import com.fasterxml.jackson.core.JsonParser;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.CRC32;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipMethod;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
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
        String canonicalManifest = validateManifest(manifestJson, version);
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

    @Transactional
    public TestBundle attach(long problemId, long versionId, byte[] archive) {
        return attach(problemId, versionId, archive, extractManifest(archive));
    }

    private String validateManifest(String manifestJson, ProblemVersion version) {
        try {
            return new TestBundleV1Contract(objectMapper)
                    .validateAndCanonicalize(version, manifestJson, properties.getMaxCases());
        } catch (TestBundleV1Contract.ContractViolation exception) {
            throw invalidBundle(exception.getMessage());
        }
    }

    private ObjectMapper strictObjectMapper() {
        return objectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    private void validateArchive(byte[] archive, String canonicalManifest) {
        assertZipSignature(archive);
        Set<String> expectedEntries = expectedArchiveEntries(canonicalManifest);
        Set<String> names = new HashSet<>();
        long totalBytes = 0;
        int entries = 0;
        try (ZipFile zip = openArchive(archive)) {
            Enumeration<ZipArchiveEntry> centralDirectory = zip.getEntries();
            while (centralDirectory.hasMoreElements()) {
                ZipArchiveEntry entry = centralDirectory.nextElement();
                entries++;
                String name = entry.getName();
                assertRegularReadableEntry(zip, entry);
                if (!safeArchivePath(name) || !names.add(name)) {
                    throw invalidBundle("test bundle contains an unsafe or duplicate ZIP entry");
                }
                if (!expectedEntries.remove(name)) {
                    throw invalidBundle("test bundle contains an undeclared ZIP entry");
                }
                boolean manifest = "manifest.json".equals(name);
                long entryLimit = manifest
                        ? properties.getMaxManifestBytes()
                        : properties.getMaxCaseBytes();
                assertCentralDirectoryLimits(entry, entryLimit);
                totalBytes = Math.addExact(totalBytes, entry.getSize());
                if (totalBytes > properties.getMaxUncompressedBytes()) {
                    throw invalidBundle("test bundle ZIP contents exceed declared limits");
                }
                byte[] contents = readEntry(zip, entry, entryLimit, !manifest);
                if (manifest
                        && !objectMapper.readTree(canonicalManifest)
                                .equals(strictObjectMapper().readTree(contents))) {
                    throw invalidBundle("manifest.json disagrees with database manifest");
                }
            }
        } catch (IOException | ArithmeticException exception) {
            throw invalidBundle("test bundle ZIP archive is invalid");
        }
        if (entries == 0 || !expectedEntries.isEmpty()) {
            throw invalidBundle("test bundle ZIP entries do not match the manifest");
        }
    }

    private Set<String> expectedArchiveEntries(String canonicalManifest) {
        try {
            Set<String> expected = new HashSet<>();
            expected.add("manifest.json");
            for (JsonNode testCase : objectMapper.readTree(canonicalManifest).get("cases")) {
                expected.add(testCase.get("input").textValue());
                expected.add(testCase.get("output").textValue());
            }
            return expected;
        } catch (JsonProcessingException exception) {
            throw invalidBundle("test bundle manifest is invalid");
        }
    }

    private String extractManifest(byte[] archive) {
        if (archive == null || archive.length == 0 || archive.length > properties.getMaxArchiveBytes()) {
            throw invalidBundle("test bundle archive size is invalid");
        }
        assertZipSignature(archive);
        try (ZipFile zip = openArchive(archive)) {
            ZipArchiveEntry manifest = null;
            for (ZipArchiveEntry candidate : zip.getEntries("manifest.json")) {
                if (manifest != null) {
                    throw invalidBundle("test bundle must contain one manifest.json");
                }
                manifest = candidate;
            }
            if (manifest == null) {
                throw invalidBundle("test bundle must contain manifest.json");
            }
            assertRegularReadableEntry(zip, manifest);
            assertCentralDirectoryLimits(manifest, properties.getMaxManifestBytes());
            return decodeUtf8(readEntry(
                    zip,
                    manifest,
                    properties.getMaxManifestBytes(),
                    false));
        } catch (IOException | ArithmeticException exception) {
            throw invalidBundle("test bundle ZIP archive is invalid");
        }
    }

    private ZipFile openArchive(byte[] archive) throws IOException {
        return ZipFile.builder()
                .setSeekableByteChannel(new SeekableInMemoryByteChannel(archive))
                .setMaxNumberOfDisks(1)
                .get();
    }

    private void assertZipSignature(byte[] archive) {
        if (archive.length < 4
                || archive[0] != 'P'
                || archive[1] != 'K'
                || archive[2] != 3
                || archive[3] != 4) {
            throw invalidBundle("test bundle is not a ZIP archive");
        }
    }

    private void assertRegularReadableEntry(ZipFile zip, ZipArchiveEntry entry) {
        int type = entry.getUnixMode() & UnixStat.FILE_TYPE_FLAG;
        if (entry.isDirectory()
                || entry.isUnixSymlink()
                || (entry.getPlatform() == ZipArchiveEntry.PLATFORM_UNIX
                        && type != UnixStat.FILE_FLAG)) {
            throw invalidBundle("test bundle contains a non-regular ZIP entry");
        }
        int method = entry.getMethod();
        if (entry.getGeneralPurposeBit().usesEncryption()
                || !zip.canReadEntryData(entry)
                || (method != ZipMethod.STORED.getCode()
                        && method != ZipMethod.DEFLATED.getCode())) {
            throw invalidBundle("test bundle contains an encrypted or unsupported ZIP entry");
        }
    }

    private void assertCentralDirectoryLimits(ZipArchiveEntry entry, long sizeLimit) {
        long size = entry.getSize();
        long compressedSize = entry.getCompressedSize();
        if (size < 0 || compressedSize < 0 || size > sizeLimit) {
            throw invalidBundle("test bundle ZIP contents exceed declared limits");
        }
        long minimumCompressedSize = size == 0
                ? 0
                : 1 + ((size - 1) / properties.getMaxCompressionRatio());
        if (compressedSize < minimumCompressedSize) {
            throw invalidBundle("test bundle ZIP entry exceeds the compression ratio");
        }
    }

    private byte[] readEntry(
            ZipFile zip,
            ZipArchiveEntry entry,
            long sizeLimit,
            boolean validateUtf8) throws IOException {
        ByteArrayOutputStream captured =
                "manifest.json".equals(entry.getName()) ? new ByteArrayOutputStream() : null;
        CharsetDecoder decoder = validateUtf8 ? strictUtf8Decoder() : null;
        ByteBuffer pending = validateUtf8 ? ByteBuffer.allocate(8196) : null;
        CharBuffer characters = validateUtf8 ? CharBuffer.allocate(8192) : null;
        CRC32 crc = new CRC32();
        long bytes = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = zip.getInputStream(entry)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes = Math.addExact(bytes, read);
                if (bytes > sizeLimit || bytes > entry.getSize()) {
                    throw invalidBundle("test bundle ZIP contents exceed declared limits");
                }
                crc.update(buffer, 0, read);
                if (captured != null) {
                    captured.write(buffer, 0, read);
                }
                if (decoder != null) {
                    validateUtf8Chunk(decoder, pending, characters, buffer, read, false);
                }
            }
        }
        if (decoder != null) {
            validateUtf8Chunk(decoder, pending, characters, buffer, 0, true);
        }
        if (bytes != entry.getSize()
                || (entry.getCrc() >= 0 && crc.getValue() != entry.getCrc())) {
            throw invalidBundle("test bundle ZIP entry integrity check failed");
        }
        return captured == null ? new byte[0] : captured.toByteArray();
    }

    private CharsetDecoder strictUtf8Decoder() {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private void validateUtf8Chunk(
            CharsetDecoder decoder,
            ByteBuffer pending,
            CharBuffer characters,
            byte[] bytes,
            int length,
            boolean endOfInput) {
        try {
            pending.put(bytes, 0, length);
            pending.flip();
            while (true) {
                CoderResult result = decoder.decode(pending, characters, endOfInput);
                characters.clear();
                if (result.isError()) {
                    result.throwException();
                }
                if (result.isUnderflow()) {
                    break;
                }
            }
            if (endOfInput) {
                while (true) {
                    CoderResult result = decoder.flush(characters);
                    characters.clear();
                    if (result.isError()) {
                        result.throwException();
                    }
                    if (result.isUnderflow()) {
                        break;
                    }
                }
            } else {
                pending.compact();
            }
        } catch (CharacterCodingException exception) {
            throw invalidBundle("test case files must be valid UTF-8");
        }
    }

    private String decodeUtf8(byte[] value) {
        try {
            return strictUtf8Decoder()
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalidBundle("manifest.json must be valid UTF-8");
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
