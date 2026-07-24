package com.zephyr.croj.model.dto.contest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import java.time.Instant;
import java.util.List;

public final class ContestRequests {
    private ContestRequests() {}

    public record Upsert(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 100_000) String descriptionMarkdown,
            @NotBlank @Size(max = 16) String ruleType,
            @NotBlank @Size(max = 16) String visibility,
            @NotNull Instant registrationOpensAt,
            @NotNull Instant registrationClosesAt,
            @NotNull Instant startsAt,
            Instant freezeAt,
            @NotNull Instant endsAt) {}

    public record ProblemItem(
            @NotNull @Positive Long problemId,
            @NotNull @Positive Long problemVersionId,
            @NotBlank @Size(max = 16) String label,
            @NotNull @Positive @Max(10_000) Integer score) {}

    public record ProblemArrangement(@NotEmpty @Size(max = 100) List<@Valid ProblemItem> problems) {}

    public record Announcement(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 100_000) String contentMarkdown) {}

    public record Clarification(
            @Positive Long problemId,
            @NotBlank @Size(max = 10_000) String questionMarkdown) {}

    public record ClarificationReply(
            @NotBlank @Size(max = 10_000) String replyMarkdown,
            boolean publicReply) {}
}
