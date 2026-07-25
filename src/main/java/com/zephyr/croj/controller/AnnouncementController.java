package com.zephyr.croj.controller;

import com.zephyr.croj.announcement.AnnouncementService;
import com.zephyr.croj.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/announcements")
@RequiredArgsConstructor
@Validated
@Tag(name = "Announcements", description = "Currently visible global announcements")
public class AnnouncementController {
    private final AnnouncementService announcements;

    @GetMapping
    @Operation(summary = "List visible global announcements")
    public Result<AnnouncementService.PublicPage> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.success(announcements.listPublic(page, size));
    }

    @GetMapping("/current")
    @Operation(summary = "List a bounded current announcement feed")
    public Result<?> current(@RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return Result.success(announcements.current(limit));
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "Read a visible global announcement")
    public Result<?> detail(@PathVariable @Min(1) long announcementId) {
        return Result.success(announcements.detail(announcementId));
    }
}
