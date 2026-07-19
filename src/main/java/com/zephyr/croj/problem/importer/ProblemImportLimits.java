package com.zephyr.croj.problem.importer;

public record ProblemImportLimits(
        int maxProblems,
        int maxTextCharacters,
        int maxTestCasesPerProblem,
        int maxEmbeddedImageBytes
) {
    public ProblemImportLimits {
        if (maxProblems <= 0 || maxTextCharacters <= 0
                || maxTestCasesPerProblem <= 0 || maxEmbeddedImageBytes <= 0) {
            throw new IllegalArgumentException("Problem import limits must be positive");
        }
    }

    public static ProblemImportLimits defaults() {
        return new ProblemImportLimits(1_000, 2_000_000, 20_000, 32 * 1024 * 1024);
    }
}
