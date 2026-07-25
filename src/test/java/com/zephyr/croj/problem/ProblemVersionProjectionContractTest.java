package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import org.junit.jupiter.api.Test;

class ProblemVersionProjectionContractTest {
    private final ProblemVersionProjectionContract contract =
            new ProblemVersionProjectionContract(new ObjectMapper());

    @Test
    void everyNonNullTotalScoreMustFitTheJudgingConsumerRange() {
        assertThrows(
                ProblemVersionProjectionContract.ContractViolation.class,
                () -> contract.assertComplete(version(0, 0)));
        assertThrows(
                ProblemVersionProjectionContract.ContractViolation.class,
                () -> contract.assertComplete(version(0, 1_000_000_001L)));
        assertDoesNotThrow(() -> contract.assertComplete(version(0, 100)));
        assertDoesNotThrow(() -> contract.assertComplete(version(0, null)));
    }

    private ProblemVersion version(int judgeMode, Number totalScore) {
        ProblemVersion version = new ProblemVersion();
        version.setStatementJson("""
                {"title":"A","description":"D","inputDescription":"I",
                 "outputDescription":"O","hints":[],"samples":[],"source":null,"tags":[]}
                """);
        version.setLimitsJson("""
                {"timeLimit":1000,"memoryLimit":64,"totalScore":%s}
                """.formatted(totalScore));
        version.setJudgeConfigJson("""
                {"specialJudge":false,"specialJudgeCode":null,"specialJudgeLanguage":null,
                 "judgeMode":%d,"checker":"exact","difficulty":2}
                """.formatted(judgeMode));
        return version;
    }
}
