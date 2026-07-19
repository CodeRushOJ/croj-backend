package com.zephyr.croj.problem.importer;

public record ProblemImportLimits(
        long maxPackageBytes,
        long maxXmlEvents,
        int maxProblems,
        int maxTextCharacters,
        int maxSampleCasesPerProblem,
        int maxTestCasesPerProblem,
        int maxCodeResourcesPerProblem,
        int maxImagesPerProblem,
        int maxEmbeddedImageBytes,
        int maxWarnings
) {
    public ProblemImportLimits {
        if (maxPackageBytes <= 0 || maxXmlEvents <= 0 || maxProblems <= 0
                || maxTextCharacters <= 0 || maxSampleCasesPerProblem <= 0
                || maxTestCasesPerProblem <= 0 || maxCodeResourcesPerProblem <= 0
                || maxImagesPerProblem <= 0 || maxEmbeddedImageBytes <= 0 || maxWarnings <= 0) {
            throw new IllegalArgumentException("Problem import limits must be positive");
        }
    }

    public static ProblemImportLimits defaults() {
        return new ProblemImportLimits(
                256L * 1024 * 1024,
                2_000_000,
                1_000,
                2_000_000,
                1_000,
                20_000,
                1_000,
                1_000,
                32 * 1024 * 1024,
                10_000);
    }
}
