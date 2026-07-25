package com.zephyr.croj.problem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class TestBundleV1Contract {
    static final int MAX_V1_CASES = 256;
    private static final Pattern CASE_ID =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Set<String> MANIFEST_FIELDS =
            Set.of("schemaVersion", "judgeMode", "checker", "limits", "cases");
    private static final Set<String> LIMIT_FIELDS =
            Set.of("timeLimitMillis", "memoryLimitMiB");
    private static final Set<String> CASE_FIELDS =
            Set.of("id", "input", "output", "weight");
    private final ObjectMapper objectMapper;

    TestBundleV1Contract(ObjectMapper objectMapper) {
        this.objectMapper =
                objectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    void assertCompatible(ProblemVersion version, JsonNode manifest) {
        if (!Boolean.TRUE.equals(version.getProjectionComplete())) {
            throw new ContractViolation("problem version is incompatible with TestBundle v1");
        }
        try {
            new ProblemVersionProjectionContract(objectMapper).assertComplete(version);
            JsonNode judge = objectMapper.readTree(version.getJudgeConfigJson());
            JsonNode limits = objectMapper.readTree(version.getLimitsJson());
            if (judge == null
                    || !judge.isObject()
                    || limits == null
                    || !limits.isObject()
                    || !isIntegralValue(judge.get("judgeMode"), 0)
                    || !isBooleanValue(judge.get("specialJudge"), false)) {
                throw new ContractViolation("problem version is incompatible with TestBundle v1");
            }
            String checker = textual(judge.get("checker"));
            if ((!"exact".equals(checker) && !"token".equals(checker))
                    || !"ACM".equals(textual(manifest.get("judgeMode")))
                    || !checker.equals(textual(manifest.get("checker")))) {
                throw new ContractViolation("problem version is incompatible with TestBundle v1");
            }
            JsonNode manifestLimits = manifest.get("limits");
            if (manifestLimits == null
                    || !manifestLimits.isObject()
                    || positiveInt(limits, "timeLimit")
                            != positiveInt(manifestLimits, "timeLimitMillis")
                    || positiveInt(limits, "memoryLimit")
                            != positiveInt(manifestLimits, "memoryLimitMiB")) {
                throw new ContractViolation("problem version is incompatible with TestBundle v1");
            }
        } catch (JsonProcessingException
                | IllegalArgumentException
                | ProblemVersionProjectionContract.ContractViolation exception) {
            throw new ContractViolation("problem version is incompatible with TestBundle v1");
        }
    }

    String validateAndCanonicalize(
            ProblemVersion version, String manifestJson, int maximumCases) {
        try {
            JsonNode root = objectMapper.readTree(manifestJson);
            JsonNode cases = root == null ? null : root.get("cases");
            JsonNode schemaVersion = root == null ? null : root.get("schemaVersion");
            JsonNode limits = root == null ? null : root.get("limits");
            if (root == null
                    || !root.isObject()
                    || cases == null
                    || !cases.isArray()
                    || !fieldNames(root).equals(MANIFEST_FIELDS)
                    || schemaVersion == null
                    || !schemaVersion.isIntegralNumber()
                    || !schemaVersion.canConvertToInt()
                    || schemaVersion.intValue() != 1
                    || !"ACM".equals(root.path("judgeMode").textValue())
                    || (!"exact".equals(root.path("checker").textValue())
                            && !"token".equals(root.path("checker").textValue()))
                    || limits == null
                    || !limits.isObject()
                    || !fieldNames(limits).equals(LIMIT_FIELDS)
                    || cases.isEmpty()
                    || cases.size() > maximumCases) {
                throw new ContractViolation("test bundle manifest cases are invalid");
            }
            assertCompatible(version, root);
            Set<String> ids = new HashSet<>();
            Set<String> paths = new HashSet<>();
            for (JsonNode testCase : cases) {
                if (!testCase.isObject() || !fieldNames(testCase).equals(CASE_FIELDS)) {
                    throw new ContractViolation("test bundle manifest case fields are invalid");
                }
                String id = requiredText(testCase, "id");
                if (!CASE_ID.matcher(id).matches()) {
                    throw new ContractViolation("test case id is invalid");
                }
                if (!ids.add(id)) {
                    throw new ContractViolation("test case ids must be unique");
                }
                String input = safeCasePath(testCase, "input");
                String output = safeCasePath(testCase, "output");
                if (!paths.add(input) || !paths.add(output)) {
                    throw new ContractViolation("test case paths must be unique");
                }
                JsonNode weight = testCase.get("weight");
                if (weight == null
                        || !weight.isIntegralNumber()
                        || !weight.canConvertToInt()
                        || weight.intValue() != 1) {
                    throw new ContractViolation("ACM test case weight must equal 1");
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new ContractViolation("test bundle manifest is invalid");
        }
    }

    private boolean isIntegralValue(JsonNode value, int expected) {
        return value != null
                && value.isIntegralNumber()
                && value.canConvertToInt()
                && value.intValue() == expected;
    }

    private boolean isBooleanValue(JsonNode value, boolean expected) {
        return value != null && value.isBoolean() && value.booleanValue() == expected;
    }

    private String textual(JsonNode value) {
        return value == null ? null : value.textValue();
    }

    private int positiveInt(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() <= 0) {
            throw new IllegalArgumentException("invalid execution limit");
        }
        return value.intValue();
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        String text = value == null ? null : value.textValue();
        if (text == null || text.isBlank()) {
            throw new ContractViolation("test case " + field + " is invalid");
        }
        return text;
    }

    private String safeCasePath(JsonNode node, String field) {
        String path = requiredText(node, field);
        if (path.length() > 512
                || !path.startsWith("cases/")
                || path.startsWith("/")
                || path.contains("\\")
                || path.indexOf('\0') >= 0
                || path.contains("//")
                || path.equals("manifest.json")
                || java.util.Arrays.asList(path.split("/")).contains("..")) {
            throw new ContractViolation("test case " + field + " path is unsafe");
        }
        return path;
    }

    static final class ContractViolation extends RuntimeException {
        ContractViolation(String message) {
            super(message);
        }
    }
}
