package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.dto.JudgeResultRequest;
import com.zephyr.croj.model.vo.JudgeResultResponse;
import com.zephyr.croj.service.JudgeResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/judge-results")
@RequiredArgsConstructor
public class InternalJudgeResultController {
    private final JudgeResultService service;

    @PostMapping
    public Result<JudgeResultResponse> ingest(@RequestBody @Valid JudgeResultRequest request) {
        return Result.success(service.ingest(request));
    }
}
