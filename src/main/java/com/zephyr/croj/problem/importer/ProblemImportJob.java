package com.zephyr.croj.problem.importer;

import java.time.Instant;

public record ProblemImportJob(
        String id,
        long actorId,
        String status,
        String detectedFormat,
        String fileSha256,
        String stagingObjectKey,
        String summaryJson,
        int importedCount,
        Instant createdAt,
        Instant expiresAt,
        Instant committedAt
) {
    public static ProblemImportJob committed(
            String id,
            long actorId,
            String format,
            String sha256,
            String objectKey,
            String summaryJson,
            int importedCount) {
        Instant now = Instant.parse("2026-07-19T08:00:00Z");
        return new ProblemImportJob(
                id, actorId, "COMMITTED", format, sha256, objectKey, summaryJson,
                importedCount, now, now.plusSeconds(86400), now);
    }
}
