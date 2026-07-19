package com.zephyr.croj.problem.importer;

import java.util.List;

public final class ProblemImportResponses {
    private ProblemImportResponses() {
    }

    public record ProblemPreview(
            String sourceId,
            String title,
            int testCaseCount,
            String status,
            List<String> errors,
            List<String> warnings
    ) {
        public ProblemPreview {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    public record Preflight(
            String jobId,
            String detectedFormat,
            String sha256,
            int problemCount,
            int testCaseCount,
            List<String> errors,
            List<String> warnings,
            List<ProblemPreview> problems
    ) {
        public Preflight {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
            problems = List.copyOf(problems);
        }
    }

    public record Commit(String jobId, String status, int importedCount) {
    }
}
