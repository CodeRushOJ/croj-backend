package com.zephyr.croj.problem.importer;

public record ProblemImportCodeResource(
        ProblemImportCodeKind kind,
        String language,
        String content
) {
}
