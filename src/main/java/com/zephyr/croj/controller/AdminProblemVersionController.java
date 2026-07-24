package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.problem.AdminTestBundleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/problems/{problemId}/versions")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-bundle", name = "enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Tag(name = "Admin TestBundle", description = "Discover immutable problem versions and manage test bundles")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminProblemVersionController {
    private final AdminTestBundleService testBundles;

    @GetMapping
    @Operation(summary = "List problem versions available to the TestBundle administration workflow")
    public Result<List<AdminTestBundleService.View>> list(@PathVariable long problemId) {
        return Result.success(testBundles.list(problemId));
    }
}
