package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.vo.AdminProblemVersionSourceVO;
import com.zephyr.croj.problem.AdminProblemVersionSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/problems/{problemId}/versions/{versionId}/source")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Tag(name = "Admin Problem Source", description = "Read private immutable checker source")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminProblemVersionSourceController {
    private final AdminProblemVersionSourceService sources;

    @GetMapping
    @Operation(summary = "Read checker source from one immutable problem version")
    public Result<AdminProblemVersionSourceVO> read(
            @PathVariable long problemId,
            @PathVariable long versionId) {
        return Result.success(sources.read(problemId, versionId));
    }
}
