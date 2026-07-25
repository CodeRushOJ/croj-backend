package com.zephyr.croj.problem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.vo.AdminProblemVersionSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminProblemVersionSourceService {
    private final ProblemVersionMapper versions;
    private final ObjectMapper objectMapper;

    public AdminProblemVersionSourceVO read(long problemId, long versionId) {
        ProblemVersion version = versions.selectById(versionId);
        if (version == null || !Long.valueOf(problemId).equals(version.getProblemId())) {
            throw TestBundleApiException.notFound();
        }
        try {
            JsonNode judge = objectMapper.readTree(version.getJudgeConfigJson());
            if (judge == null || !judge.isObject()) {
                throw new BusinessException(ResultCodeEnum.SYSTEM_ERROR);
            }
            JsonNode specialJudge = judge.get("specialJudge");
            JsonNode judgeMode = judge.get("judgeMode");
            if (specialJudge == null
                    || !specialJudge.isBoolean()
                    || judgeMode == null
                    || !judgeMode.isIntegralNumber()
                    || !judgeMode.canConvertToInt()) {
                throw new BusinessException(ResultCodeEnum.SYSTEM_ERROR);
            }
            return new AdminProblemVersionSourceVO(
                    problemId,
                    versionId,
                    version.getVersionNo(),
                    version.getState(),
                    specialJudge.booleanValue(),
                    nullableText(judge.get("specialJudgeCode")),
                    nullableText(judge.get("specialJudgeLanguage")),
                    judgeMode.intValue());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_ERROR);
        }
    }

    private String nullableText(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_ERROR);
        }
        return value.textValue();
    }
}
