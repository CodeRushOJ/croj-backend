package com.zephyr.croj.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JudgeResultRequest {
    @NotBlank @Size(max = 128)
    private String resultId;
    @NotNull @Positive
    private Long submissionId;
    @NotNull @Min(1)
    private Integer attemptNo;
    @NotBlank @Size(max = 32)
    private String status;
    @NotNull
    private Integer exitCode;
    @NotNull @Min(0) @Max(86_400_000)
    private Integer timeUsedMillis;
    @NotNull @Min(0) @Max(2_147_483_647L)
    private Integer memoryUsedKb;
    @Min(0) @Max(1_000_000_000)
    private Integer score;
    @Min(1) @Max(1_000_000_000)
    private Integer totalScore;
    @Size(max = 65_536)
    private String stdout;
    @Size(max = 65_536)
    private String stderr;
    @Size(max = 32_768)
    private String compileError;

    @JsonIgnore
    @AssertTrue(message = "score and totalScore must be present together")
    public boolean isScoreContractComplete() {
        return (score == null) == (totalScore == null)
                && (score == null || score <= totalScore);
    }
}
