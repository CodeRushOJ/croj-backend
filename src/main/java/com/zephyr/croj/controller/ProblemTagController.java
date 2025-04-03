package com.zephyr.croj.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.entity.ProblemTag;
import com.zephyr.croj.model.vo.ProblemTagVO;
import com.zephyr.croj.service.ProblemTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 问题标签控制器
 */
@RestController
@RequestMapping("/problem/tag")
@Tag(name = "问题标签管理", description = "问题标签相关接口")
@Slf4j
@RequiredArgsConstructor
public class ProblemTagController {

    private final ProblemTagService problemTagService;
    private final HttpServletRequest request;

    /**
     * 创建标签
     */
    @PostMapping
    @Operation(
            summary = "创建标签",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Long> createTag(@RequestBody @Valid ProblemTag tag) {
        Long userId = getUserId();
        Long tagId = problemTagService.createTag(tag, userId);
        return Result.success("创建成功", tagId);
    }

    /**
     * 更新标签
     */
    @PutMapping
    @Operation(
            summary = "更新标签",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Boolean> updateTag(@RequestBody @Valid ProblemTag tag) {
        Long userId = getUserId();
        boolean success = problemTagService.updateTag(tag, userId);
        return Result.success("更新成功", success);
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除标签",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Boolean> deleteTag(@PathVariable Long id) {
        Long userId = getUserId();
        boolean success = problemTagService.deleteTag(id, userId);
        return Result.success("删除成功", success);
    }

    /**
     * 获取所有标签
     */
    @GetMapping("/all")
    @Operation(
            summary = "获取所有标签",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<List<ProblemTagVO>> getAllTags() {
        List<ProblemTagVO> tags = problemTagService.getAllTags();
        return Result.success(tags);
    }

    /**
     * 获取标签列表（分页）
     */
    @GetMapping("/list")
    @Operation(
            summary = "获取标签列表",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<IPage<ProblemTagVO>> getTagList(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword) {
        IPage<ProblemTagVO> tags = problemTagService.getTagList(current, size, keyword);
        return Result.success(tags);
    }

    /**
     * 根据ID获取标签
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "根据ID获取标签",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<ProblemTagVO> getTagById(@PathVariable Long id) {
        ProblemTagVO tag = problemTagService.getTagById(id);
        return Result.success(tag);
    }

    /**
     * 获取题目的标签列表
     */
    @GetMapping("/problem/{problemId}")
    @Operation(
            summary = "获取题目的标签列表",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<List<ProblemTagVO>> getTagsByProblemId(@PathVariable Long problemId) {
        List<ProblemTagVO> tags = problemTagService.getTagsByProblemId(problemId);
        return Result.success(tags);
    }

    /**
     * 从请求中获取用户ID
     */
    private Long getUserId() {
        Object userIdObj = request.getAttribute("userId");
        return userIdObj != null ? (Long) userIdObj : null;
    }
}