package com.zephyr.croj.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.dto.PublishSolutionDTO;
import com.zephyr.croj.model.vo.SolutionVO;
import com.zephyr.croj.service.CommunityContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/problems/{problemId}/solutions")
@Tag(name = "题解")
@Validated
@RequiredArgsConstructor
public class SolutionController {
    private final CommunityContentService content;
    private final HttpServletRequest request;

    @GetMapping
    @Operation(summary = "公开题目的已发布题解")
    public Result<IPage<SolutionVO>> listSolutions(
            @PathVariable @Positive Long problemId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long size) {
        return Result.success(content.listSolutions(problemId, current, size));
    }

    @PostMapping
    @Operation(summary = "发布题解")
    @SecurityRequirement(name = "Bearer Authentication")
    public Result<Long> publishSolution(
            @PathVariable @Positive Long problemId,
            @RequestBody @Valid PublishSolutionDTO body) {
        return Result.success("发布成功", content.publishSolution(problemId, body, actorId()));
    }

    @GetMapping("/{solutionId}")
    @Operation(summary = "题解详情")
    public Result<SolutionVO> getSolution(
            @PathVariable @Positive Long problemId,
            @PathVariable @Positive Long solutionId) {
        return Result.success(content.getSolution(problemId, solutionId));
    }

    @DeleteMapping("/{solutionId}")
    @Operation(summary = "删除自己的题解；管理员可执行内容治理")
    @SecurityRequirement(name = "Bearer Authentication")
    public Result<Void> deleteSolution(
            @PathVariable @Positive Long problemId,
            @PathVariable @Positive Long solutionId) {
        content.deleteSolution(problemId, solutionId, actorId());
        return Result.success();
    }

    private Long actorId() {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        return id;
    }
}
