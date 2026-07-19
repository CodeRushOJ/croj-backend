package com.zephyr.croj.controller;

import com.zephyr.croj.announcement.AnnouncementRequests;
import com.zephyr.croj.announcement.AnnouncementService;
import com.zephyr.croj.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/announcements")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Tag(name = "Announcement administration", description = "Global announcement lifecycle management")
public class AdminAnnouncementController {
    private final AnnouncementService announcements;
    private final HttpServletRequest request;

    @GetMapping
    @Operation(summary = "List global announcements in every effective lifecycle")
    public Result<AnnouncementService.AdminPage> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        return Result.success(announcements.listAdmin(page, size, status));
    }

    @PostMapping
    @Operation(summary = "Create a global announcement draft")
    public Result<Long> create(@RequestBody @Valid AnnouncementRequests.Draft body) {
        return Result.success(announcements.create(body, administratorId()));
    }

    @PutMapping("/{announcementId}")
    @Operation(summary = "Update announcement content and pin order")
    public Result<Void> update(
            @PathVariable @Min(1) long announcementId,
            @RequestBody @Valid AnnouncementRequests.Draft body) {
        announcements.update(announcementId, body, administratorId());
        return Result.success();
    }

    @PostMapping("/{announcementId}/schedule")
    @Operation(summary = "Schedule an announcement for future publication")
    public Result<Void> schedule(
            @PathVariable @Min(1) long announcementId,
            @RequestBody @Valid AnnouncementRequests.Schedule body) {
        announcements.schedule(announcementId, body, administratorId());
        return Result.success();
    }

    @PostMapping("/{announcementId}/publish")
    @Operation(summary = "Publish an announcement immediately")
    public Result<Void> publish(
            @PathVariable @Min(1) long announcementId,
            @RequestBody @Valid AnnouncementRequests.Publish body) {
        announcements.publish(announcementId, body, administratorId());
        return Result.success();
    }

    @PostMapping("/{announcementId}/withdraw")
    @Operation(summary = "Withdraw a scheduled or published announcement to draft")
    public Result<Void> withdraw(@PathVariable @Min(1) long announcementId) {
        announcements.withdraw(announcementId, administratorId());
        return Result.success();
    }

    @PostMapping("/{announcementId}/archive")
    @Operation(summary = "Archive an announcement permanently")
    public Result<Void> archive(@PathVariable @Min(1) long announcementId) {
        announcements.archive(announcementId, administratorId());
        return Result.success();
    }

    private long administratorId() {
        Object value = request.getAttribute("userId");
        if (value instanceof Long id) {
            return id;
        }
        throw new IllegalStateException("authenticated administrator userId is missing");
    }
}
