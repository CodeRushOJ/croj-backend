package com.zephyr.croj.announcement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AnnouncementRequests {
    private AnnouncementRequests() {}

    public record Draft(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 100_000) String contentMarkdown,
            boolean pinned,
            @Min(0) @Max(10_000) int pinOrder) {}

    public record Schedule(@NotNull Instant publishAt, Instant expiresAt) {}

    public record Publish(Instant expiresAt) {}
}
