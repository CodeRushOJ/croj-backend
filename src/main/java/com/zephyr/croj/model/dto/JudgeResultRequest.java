package com.zephyr.croj.model.dto;

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
    @Size(max = 65_536)
    private String stdout;
    @Size(max = 65_536)
    private String stderr;
    @Size(max = 32_768)
    private String compileError;
}
