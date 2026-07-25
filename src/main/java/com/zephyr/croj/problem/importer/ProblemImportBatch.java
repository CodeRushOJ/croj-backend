package com.zephyr.croj.problem.importer;

import java.util.List;

public record ProblemImportBatch(
        ProblemPackageFormat format,
        String formatVersion,
        String sourceUrl,
        List<ProblemImportDraft> problems,
        List<String> warnings
) {
    public ProblemImportBatch {
        problems = List.copyOf(problems);
        warnings = List.copyOf(warnings);
    }
}
