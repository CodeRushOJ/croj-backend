package com.zephyr.croj.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.dto.ProblemCreateDTO;
import com.zephyr.croj.model.dto.ProblemQueryDTO;
import com.zephyr.croj.model.dto.ProblemUpdateDTO;
import com.zephyr.croj.model.vo.ProblemListItemVO;
import com.zephyr.croj.model.vo.ProblemVO;
import com.zephyr.croj.service.ProblemService;
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

/**
 * 问题控制器
 */
@RestController
@RequestMapping("/problem")
@Tag(name = "问题管理", description = "问题相关接口")
@Slf4j
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final HttpServletRequest request;

    /**
     * 创建问题
     */
    @PostMapping
    @Operation(
            summary = "创建问题",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Long> createProblem(@RequestBody @Valid ProblemCreateDTO dto) {
        Long userId = getUserId();
        Long problemId = problemService.createProblem(dto, userId);
        return Result.success("创建成功", problemId);
    }

    /**
     * 更新问题
     */
    @PutMapping
    @Operation(
            summary = "更新问题",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Boolean> updateProblem(@RequestBody @Valid ProblemUpdateDTO dto) {
        Long userId = getUserId();
        boolean success = problemService.updateProblem(dto, userId);
        return Result.success("更新成功", success);
    }

    /**
     * 删除问题
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除问题",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Boolean> deleteProblem(@PathVariable Long id) {
        Long userId = getUserId();
        boolean success = problemService.deleteProblem(id, userId);
        return Result.success("删除成功", success);
    }

    /**
     * 根据ID获取问题详情
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "根据ID获取问题详情",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<ProblemVO> getProblemById(@PathVariable Long id) {
        Long userId = getUserId();
        ProblemVO problem = problemService.getProblemById(id, userId);
        return Result.success(problem);
    }

    /**
     * 根据题目编号获取问题详情
     */
    @GetMapping("/no/{problemNo}")
    @Operation(
            summary = "根据题目编号获取问题详情",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<ProblemVO> getProblemByNo(@PathVariable String problemNo) {
        Long userId = getUserId();
        ProblemVO problem = problemService.getProblemByNo(problemNo, userId);
        return Result.success(problem);
    }

    /**
     * 获取问题列表
     */
    @PostMapping("/list")
    @Operation(
            summary = "获取问题列表",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<IPage<ProblemListItemVO>> getProblemList(@RequestBody @Valid ProblemQueryDTO queryDTO) {
        Long userId = getUserId();
        IPage<ProblemListItemVO> problems = problemService.getProblemList(queryDTO, userId);
        return Result.success(problems);
    }

    /**
     * 从请求中获取用户ID
     */
    private Long getUserId() {
        Object userIdObj = request.getAttribute("userId");
        return userIdObj != null ? (Long) userIdObj : null;
    }
}