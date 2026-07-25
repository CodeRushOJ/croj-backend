package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.contest.ContestScoreboardService;
import com.zephyr.croj.contest.ContestService;
import com.zephyr.croj.model.dto.contest.ContestRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/contests")
@RequiredArgsConstructor
public class ContestController {
    private final ContestService contests;
    private final ContestScoreboardService scoreboards;
    private final HttpServletRequest request;

    @GetMapping
    public Result<ContestService.ContestPage> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(contests.listPublic(page, size));
    }

    @GetMapping("/{contestId}")
    public Result<ContestService.ContestDetail> detail(@PathVariable long contestId) {
        return Result.success(contests.detail(contestId, userId(), isAdministrator()));
    }

    @GetMapping("/{contestId}/me")
    @SecurityRequirement(name = "Bearer Authentication")
    public Result<ContestService.RegistrationStatus> me(@PathVariable long contestId) {
        return Result.success(contests.registrationStatus(contestId, requiredUserId()));
    }

    @PostMapping("/{contestId}/registrations")
    @SecurityRequirement(name = "Bearer Authentication")
    public Result<String> register(@PathVariable long contestId) {
        return Result.success(contests.register(contestId, requiredUserId()));
    }

    @DeleteMapping("/{contestId}/registrations/me")
    @SecurityRequirement(name = "Bearer Authentication")
    public Result<String> cancelRegistration(@PathVariable long contestId) {
        return Result.success(contests.cancelRegistration(contestId, requiredUserId()));
    }

    @GetMapping("/{contestId}/problems")
    public Result<?> problems(@PathVariable long contestId) {
        return Result.success(contests.problems(contestId, userId(), isAdministrator()));
    }

    @GetMapping("/{contestId}/announcements")
    public Result<?> announcements(@PathVariable long contestId) {
        return Result.success(contests.announcements(contestId, userId(), isAdministrator()));
    }

    @GetMapping("/{contestId}/clarifications")
    public Result<?> clarifications(@PathVariable long contestId) {
        return Result.success(contests.clarifications(contestId, userId(), isAdministrator()));
    }

    @PostMapping("/{contestId}/clarifications")
    @SecurityRequirement(name = "Bearer Authentication")
    public Result<Long> ask(
            @PathVariable long contestId,
            @RequestBody @Valid ContestRequests.Clarification clarification) {
        return Result.success(contests.ask(contestId, requiredUserId(), clarification));
    }

    @GetMapping("/{contestId}/scoreboard")
    @Operation(
            summary = "Read the public contest scoreboard",
            description =
                    "Returns an explicitly typed ACM or OI board. ACM rows sort by solved desc, penalty asc and last accepted time; OI rows sort by total score desc, scored problems desc and last improvement time. During freeze the public board uses the exclusive [start, freeze) submission window, while the final board uses [start, end). ACM-only and OI-only row fields are mutually exclusive and nullable.")
    public Result<ContestScoreboardService.ScoreboardView> scoreboard(@PathVariable long contestId) {
        return Result.success(scoreboards.publicScoreboard(contestId, userId()));
    }

    private Long userId() {
        Object value = request.getAttribute("userId");
        return value instanceof Long id ? id : null;
    }

    private long requiredUserId() {
        Long value = userId();
        if (value == null) {
            throw new IllegalStateException("authenticated userId is missing");
        }
        return value;
    }

    private boolean isAdministrator() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                                || authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}
