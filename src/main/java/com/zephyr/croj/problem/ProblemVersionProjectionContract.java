package com.zephyr.croj.problem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class ProblemVersionProjectionContract {
    private final ObjectMapper objectMapper;

    public ProblemVersionProjectionContract(ObjectMapper objectMapper) {
        this.objectMapper =
                objectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public void assertComplete(ProblemVersion version) {
        try {
            JsonNode statement = objectMapper.readTree(version.getStatementJson());
            JsonNode limits = objectMapper.readTree(version.getLimitsJson());
            JsonNode judge = objectMapper.readTree(version.getJudgeConfigJson());
            requireObject(statement);
            requireText(statement, "title", true);
            requireText(statement, "description", true);
            requireText(statement, "inputDescription", true);
            requireText(statement, "outputDescription", true);
            requireNullableText(statement, "source");
            requireStringArray(statement, "hints");
            requireSamples(statement);
            requireTags(statement);

            requireObject(limits);
            requirePositiveInt(limits, "timeLimit");
            requirePositiveInt(limits, "memoryLimit");
            requireNullableNonNegativeInt(limits, "totalScore");

            requireObject(judge);
            requireBoolean(judge, "specialJudge");
            requireNullableText(judge, "specialJudgeCode");
            requireNullableText(judge, "specialJudgeLanguage");
            requireIntRange(judge, "judgeMode", 0, 1);
            requireIntRange(judge, "difficulty", 1, 3);
            String checker = requireText(judge, "checker", true);
            if (!"exact".equals(checker) && !"token".equals(checker)) {
                throw new ContractViolation("judge.checker");
            }
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ContractViolation("invalid JSON");
        }
    }

    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new ContractViolation("object");
        }
    }

    private String requireText(JsonNode object, String field, boolean nonBlank) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || (nonBlank && value.textValue().isBlank())) {
            throw new ContractViolation(field);
        }
        return value.textValue();
    }

    private void requireNullableText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || (!value.isNull() && !value.isTextual())) {
            throw new ContractViolation(field);
        }
    }

    private void requireStringArray(JsonNode object, String field) {
        JsonNode values = object.get(field);
        if (values == null || !values.isArray()) {
            throw new ContractViolation(field);
        }
        values.forEach(value -> {
            if (!value.isTextual()) {
                throw new ContractViolation(field);
            }
        });
    }

    private void requireSamples(JsonNode statement) {
        JsonNode samples = statement.get("samples");
        if (samples == null || !samples.isArray()) {
            throw new ContractViolation("samples");
        }
        for (JsonNode sample : samples) {
            if (!sample.isObject()) {
                throw new ContractViolation("samples");
            }
            Iterator<JsonNode> values = sample.elements();
            while (values.hasNext()) {
                if (!values.next().isTextual()) {
                    throw new ContractViolation("samples");
                }
            }
        }
    }

    private void requireTags(JsonNode statement) {
        JsonNode tags = statement.get("tags");
        if (tags == null || !tags.isArray()) {
            throw new ContractViolation("tags");
        }
        Set<Long> ids = new HashSet<>();
        for (JsonNode tag : tags) {
            if (!tag.isObject() || fieldNames(tag).size() != 3
                    || !fieldNames(tag).equals(Set.of("id", "name", "color"))) {
                throw new ContractViolation("tags");
            }
            JsonNode id = tag.get("id");
            if (id == null
                    || !id.isIntegralNumber()
                    || !id.canConvertToLong()
                    || id.longValue() <= 0
                    || !ids.add(id.longValue())) {
                throw new ContractViolation("tags.id");
            }
            requireText(tag, "name", true);
            requireText(tag, "color", true);
        }
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new HashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private void requirePositiveInt(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() <= 0) {
            throw new ContractViolation(field);
        }
    }

    private void requireNullableNonNegativeInt(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null
                || (!value.isNull()
                        && (!value.isIntegralNumber()
                                || !value.canConvertToInt()
                                || value.intValue() < 0))) {
            throw new ContractViolation(field);
        }
    }

    private void requireBoolean(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isBoolean()) {
            throw new ContractViolation(field);
        }
    }

    private void requireIntRange(JsonNode object, String field, int minimum, int maximum) {
        JsonNode value = object.get(field);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() < minimum
                || value.intValue() > maximum) {
            throw new ContractViolation(field);
        }
    }

    public static final class ContractViolation extends RuntimeException {
        ContractViolation(String message) {
            super(message);
        }
    }
}
