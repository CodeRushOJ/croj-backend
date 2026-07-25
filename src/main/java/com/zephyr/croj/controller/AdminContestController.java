package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.contest.ContestAdminService;
import com.zephyr.croj.contest.ContestScoreboardService;
import com.zephyr.croj.model.dto.contest.ContestRequests;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/contests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminContestController {
    private final ContestAdminService contests;
    private final ContestScoreboardService scoreboards;
    private final HttpServletRequest request;

    @PostMapping
    public Result<Long> create(@RequestBody @Valid ContestRequests.Upsert body) {
        return Result.success(contests.create(body, administratorId()));
    }

    @PutMapping("/{contestId}")
    public Result<Void> update(@PathVariable long contestId, @RequestBody @Valid ContestRequests.Upsert body) {
        contests.update(contestId, body);
        return Result.success();
    }

    @DeleteMapping("/{contestId}")
    public Result<Void> cancel(@PathVariable long contestId) {
        contests.cancel(contestId);
        return Result.success();
    }

    @PutMapping("/{contestId}/problems")
    public Result<Void> arrange(
            @PathVariable long contestId,
            @RequestBody @Valid ContestRequests.ProblemArrangement body) {
        contests.arrangeProblems(contestId, body);
        return Result.success();
    }

    @PostMapping("/{contestId}/publish")
    public Result<Void> publish(@PathVariable long contestId) {
        contests.publish(contestId);
        return Result.success();
    }

    @PostMapping("/{contestId}/registrations/{userId}")
    public Result<String> register(@PathVariable long contestId, @PathVariable long userId) {
        return Result.success(contests.manageRegistration(contestId, userId, administratorId(), true));
    }

    @DeleteMapping("/{contestId}/registrations/{userId}")
    public Result<String> removeRegistration(@PathVariable long contestId, @PathVariable long userId) {
        return Result.success(contests.manageRegistration(contestId, userId, administratorId(), false));
    }

    @PostMapping("/{contestId}/announcements")
    public Result<Long> announce(
            @PathVariable long contestId,
            @RequestBody @Valid ContestRequests.Announcement body) {
        return Result.success(contests.announce(contestId, body, administratorId()));
    }

    @PostMapping("/{contestId}/clarifications/{clarificationId}/replies")
    public Result<Long> reply(
            @PathVariable long contestId,
            @PathVariable long clarificationId,
            @RequestBody @Valid ContestRequests.ClarificationReply body) {
        return Result.success(contests.reply(contestId, clarificationId, body, administratorId()));
    }

    @GetMapping("/{contestId}/scoreboard")
    public Result<ContestScoreboardService.ScoreboardView> scoreboard(@PathVariable long contestId) {
        return Result.success(scoreboards.administratorScoreboard(contestId));
    }

    private long administratorId() {
        Object value = request.getAttribute("userId");
        if (value instanceof Long id) {
            return id;
        }
        throw new IllegalStateException("authenticated administrator userId is missing");
    }
}
