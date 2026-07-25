package com.zephyr.croj.problem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Backend producer-side validation for the immutable TestBundle contract consumed by
 * croj-judging-server. Schema v1 remains supported permanently; schema v2 adds OI scoring and a
 * sandboxed special checker.
 */
final class TestBundleManifestContract {
    static final int MAX_CASES = 256;
    static final int MAX_TOTAL_SCORE = 1_000_000_000;
    static final int MAX_EXECUTION_MILLIS = 86_400_000;
    static final int MAX_SPECIAL_JUDGE_SOURCE_BYTES = 4 << 20;
    private static final Pattern CASE_ID =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern LOWER_SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> V1_MANIFEST_FIELDS =
            Set.of("schemaVersion", "judgeMode", "checker", "limits", "cases");
    private static final Set<String> V2_MANIFEST_FIELDS =
            Set.of("schemaVersion", "judgeMode", "checker", "limits", "totalScore",
                    "specialJudge", "cases");
    private static final Set<String> LIMIT_FIELDS =
            Set.of("timeLimitMillis", "memoryLimitMiB");
    private static final Set<String> SPECIAL_JUDGE_FIELDS =
            Set.of("language", "source", "sourceSha256", "timeLimitMillis", "memoryLimitMiB");
    private static final Set<String> CASE_FIELDS =
            Set.of("id", "input", "output", "weight");
    private static final Set<String> LANGUAGES =
            Set.of("go", "cpp", "python", "java", "javascript");
    private final ObjectMapper objectMapper;

    TestBundleManifestContract(ObjectMapper objectMapper) {
        this.objectMapper =
                objectMapper
                        .copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    String validateAndCanonicalize(
            ProblemVersion version, String manifestJson, int maximumCases) {
        try {
            JsonNode root = objectMapper.readTree(manifestJson);
            if (root == null || !root.isObject()) {
                throw violation("test bundle manifest is invalid");
            }
            int schemaVersion = requiredInt(root, "schemaVersion", 1, 2);
            Set<String> expectedFields =
                    schemaVersion == 1 ? V1_MANIFEST_FIELDS : requiredV2Fields(root);
            if (!fieldNames(root).equals(expectedFields)) {
                throw violation("test bundle manifest fields are invalid");
            }
            String judgeMode = requiredText(root, "judgeMode");
            String checker = requiredText(root, "checker");
            validateModeAndChecker(schemaVersion, judgeMode, checker);
            validateLimits(root.get("limits"));

            JsonNode cases = root.get("cases");
            if (cases == null
                    || !cases.isArray()
                    || cases.isEmpty()
                    || cases.size() > maximumCases
                    || cases.size() > MAX_CASES) {
                throw violation("test bundle manifest cases are invalid");
            }
            Set<String> ids = new HashSet<>();
            Set<String> paths = new HashSet<>();
            JsonNode specialJudge = root.get("specialJudge");
            if ("special".equals(checker)) {
                validateSpecialJudge(specialJudge, paths);
            } else if (specialJudge != null) {
                throw violation("specialJudge is only valid for the special checker");
            }

            long weightSum = 0;
            for (JsonNode testCase : cases) {
                if (!testCase.isObject() || !fieldNames(testCase).equals(CASE_FIELDS)) {
                    throw violation("test bundle manifest case fields are invalid");
                }
                String id = requiredText(testCase, "id");
                if (!CASE_ID.matcher(id).matches() || !ids.add(id)) {
                    throw violation("test case id is invalid or duplicated");
                }
                String input = safeArtifactPath(testCase, "input");
                String output = safeArtifactPath(testCase, "output");
                if (!paths.add(input) || !paths.add(output)) {
                    throw violation("test case paths must be unique");
                }
                int weight = requiredInt(testCase, "weight", 1, MAX_TOTAL_SCORE);
                if ("ACM".equals(judgeMode) && weight != 1) {
                    throw violation("ACM test case weight must equal 1");
                }
                weightSum = Math.addExact(weightSum, weight);
                if (weightSum > MAX_TOTAL_SCORE) {
                    throw violation("OI case weights exceed supported total");
                }
            }
            if ("OI".equals(judgeMode)) {
                int totalScore = requiredInt(root, "totalScore", 1, MAX_TOTAL_SCORE);
                if (weightSum != totalScore) {
                    throw violation("OI totalScore must equal the sum of case weights");
                }
            } else if (root.has("totalScore")) {
                throw violation("totalScore is only valid for OI");
            }
            assertCompatible(version, root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException | ArithmeticException exception) {
            throw violation("test bundle manifest is invalid");
        }
    }

    void assertCompatible(ProblemVersion version, JsonNode manifest) {
        String incompatibility = manifest.path("schemaVersion").asInt() == 1
                ? "problem version is incompatible with TestBundle v1"
                : "problem version is incompatible with TestBundle v2";
        if (!Boolean.TRUE.equals(version.getProjectionComplete())) {
            throw violation(incompatibility);
        }
        try {
            new ProblemVersionProjectionContract(objectMapper).assertComplete(version);
            JsonNode judge = objectMapper.readTree(version.getJudgeConfigJson());
            JsonNode limits = objectMapper.readTree(version.getLimitsJson());
            int mode = requiredInt(judge, "judgeMode", 0, 1);
            String expectedMode = mode == 0 ? "ACM" : "OI";
            String checker = requiredText(judge, "checker");
            if (!expectedMode.equals(requiredText(manifest, "judgeMode"))
                    || !checker.equals(requiredText(manifest, "checker"))
                    || positiveInt(limits, "timeLimit")
                            != positiveInt(manifest.get("limits"), "timeLimitMillis")
                    || positiveInt(limits, "memoryLimit")
                            != positiveInt(manifest.get("limits"), "memoryLimitMiB")) {
                throw violation(incompatibility);
            }
            boolean special = requiredBoolean(judge, "specialJudge");
            if (special != "special".equals(checker)) {
                throw violation(incompatibility);
            }
            if (mode == 1) {
                int immutableTotal = positiveInt(limits, "totalScore");
                if (immutableTotal != positiveInt(manifest, "totalScore")) {
                    throw violation(incompatibility);
                }
            }
            if (special) {
                JsonNode specialJudge = manifest.get("specialJudge");
                String source = requiredText(judge, "specialJudgeCode");
                String language = requiredText(judge, "specialJudgeLanguage");
                if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                                > MAX_SPECIAL_JUDGE_SOURCE_BYTES
                        || !language.equals(requiredText(specialJudge, "language"))
                        || !sha256(source).equals(requiredText(specialJudge, "sourceSha256"))) {
                    throw violation(incompatibility);
                }
            }
        } catch (JsonProcessingException
                | IllegalArgumentException
                | ProblemVersionProjectionContract.ContractViolation exception) {
            throw violation(incompatibility);
        }
    }

    private Set<String> requiredV2Fields(JsonNode root) {
        Set<String> expected = new HashSet<>(V2_MANIFEST_FIELDS);
        if (!"OI".equals(root.path("judgeMode").textValue())) {
            expected.remove("totalScore");
        }
        if (!"special".equals(root.path("checker").textValue())) {
            expected.remove("specialJudge");
        }
        return Set.copyOf(expected);
    }

    private void validateModeAndChecker(int schemaVersion, String judgeMode, String checker) {
        if (schemaVersion == 1) {
            if (!"ACM".equals(judgeMode)
                    || (!"exact".equals(checker) && !"token".equals(checker))) {
                throw violation("manifest v1 supports ACM exact/token only");
            }
            return;
        }
        if ((!"ACM".equals(judgeMode) && !"OI".equals(judgeMode))
                || (!"exact".equals(checker)
                        && !"token".equals(checker)
                        && !"special".equals(checker))) {
            throw violation("manifest v2 judge mode or checker is unsupported");
        }
    }

    private void validateLimits(JsonNode limits) {
        if (limits == null
                || !limits.isObject()
                || !fieldNames(limits).equals(LIMIT_FIELDS)) {
            throw violation("test bundle limits are invalid");
        }
        requiredInt(limits, "timeLimitMillis", 1, MAX_EXECUTION_MILLIS);
        requiredInt(limits, "memoryLimitMiB", 1, Integer.MAX_VALUE);
    }

    private void validateSpecialJudge(JsonNode specialJudge, Set<String> paths) {
        if (specialJudge == null
                || !specialJudge.isObject()
                || !fieldNames(specialJudge).equals(SPECIAL_JUDGE_FIELDS)
                || !LANGUAGES.contains(requiredText(specialJudge, "language"))) {
            throw violation("specialJudge configuration is invalid");
        }
        String source = safeArtifactPath(specialJudge, "source");
        if (!paths.add(source)) {
            throw violation("specialJudge source path is duplicated");
        }
        if (!LOWER_SHA256.matcher(requiredText(specialJudge, "sourceSha256")).matches()) {
            throw violation("specialJudge sourceSha256 must be lowercase SHA-256");
        }
        requiredInt(specialJudge, "timeLimitMillis", 1, MAX_EXECUTION_MILLIS);
        requiredInt(specialJudge, "memoryLimitMiB", 1, Integer.MAX_VALUE);
    }

    private String safeArtifactPath(JsonNode node, String field) {
        String path = requiredText(node, field);
        if (path.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 512
                || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(path)
                || path.startsWith("/")
                || path.contains("\\")
                || path.indexOf('\0') >= 0
                || path.contains("//")
                || path.equals(".")
                || path.equals("manifest.json")
                || path.endsWith("/")
                || java.util.Arrays.asList(path.split("/")).contains("..")
                || java.util.Arrays.asList(path.split("/")).contains(".")) {
            throw violation("artifact path is unsafe");
        }
        return path;
    }

    private int positiveInt(JsonNode object, String field) {
        return requiredInt(object, field, 1, Integer.MAX_VALUE);
    }

    private int requiredInt(JsonNode object, String field, int minimum, int maximum) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() < minimum
                || value.intValue() > maximum) {
            throw violation(field + " is invalid");
        }
        return value.intValue();
    }

    private boolean requiredBoolean(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isBoolean()) {
            throw violation(field + " is invalid");
        }
        return value.booleanValue();
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        String text = value == null ? null : value.textValue();
        if (text == null || text.isBlank()) {
            throw violation(field + " is invalid");
        }
        return text;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ContractViolation violation(String message) {
        return new ContractViolation(message);
    }

    static final class ContractViolation extends RuntimeException {
        ContractViolation(String message) {
            super(message);
        }
    }
}
