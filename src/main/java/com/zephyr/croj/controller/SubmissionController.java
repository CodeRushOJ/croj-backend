package com.zephyr.croj.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.dto.SubmissionDTO;
import com.zephyr.croj.model.dto.SubmissionQueryDTO;
import com.zephyr.croj.model.vo.SubmissionVO;
import com.zephyr.croj.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 提交记录控制器
 */
@RestController
@RequestMapping("/submission")
@Tag(name = "提交记录管理", description = "提交记录相关接口")
@Slf4j
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final HttpServletRequest request;

    /**
     * 提交代码
     */
    @PostMapping
    @Operation(
            summary = "提交代码",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<Long> submitCode(@RequestBody @Valid SubmissionDTO dto) {
        Long userId = getUserId();
        Long submissionId = submissionService.submitCode(dto, userId);
        return Result.success("提交成功", submissionId);
    }

    /**
     * 获取提交详情
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "获取提交详情",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<SubmissionVO> getSubmission(@PathVariable Long id) {
        Long userId = getUserId();
        SubmissionVO submission = submissionService.getSubmissionById(id, userId);
        return Result.success(submission);
    }

    /**
     * 获取提交列表
     */
    @PostMapping("/list")
    @Operation(
            summary = "获取提交列表",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<IPage<SubmissionVO>> getSubmissionList(@RequestBody @Valid SubmissionQueryDTO queryDTO) {
        Long userId = getUserId();
        IPage<SubmissionVO> submissions = submissionService.getSubmissionList(queryDTO, userId);
        return Result.success(submissions);
    }

    /**
     * 获取用户最佳提交（特定题目）
     */
    @GetMapping("/best")
    @Operation(
            summary = "获取用户最佳提交",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<SubmissionVO> getUserBestSubmission(
            @Parameter(description = "题目ID") @RequestParam Long problemId) {
        Long userId = getUserId();
        SubmissionVO submission = submissionService.getUserBestSubmission(userId, problemId);
        return Result.success(submission);
    }

    /**
     * 从请求中获取用户ID
     */
    private Long getUserId() {
        Object userIdObj = request.getAttribute("userId");
        return userIdObj != null ? (Long) userIdObj : null;
    }
}