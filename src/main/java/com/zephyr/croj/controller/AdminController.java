package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员控制器
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "管理员功能", description = "管理员相关接口")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 获取管理统计数据
     */
    @GetMapping("/statistics")
    @Operation(
            summary = "获取管理统计数据",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> statistics = adminService.getStatistics();
        return Result.success(statistics);
    }
}