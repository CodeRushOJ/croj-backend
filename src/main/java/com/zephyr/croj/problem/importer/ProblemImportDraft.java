package com.zephyr.croj.problem.importer;

import java.util.List;

public record ProblemImportDraft(
        String title,
        String description,
        String inputDescription,
        String outputDescription,
        String hint,
        String source,
        int timeLimitMillis,
        int memoryLimitKilobytes,
        List<ProblemImportCase> samples,
        List<ProblemImportCase> tests,
        List<ProblemImportCodeResource> codeResources,
        List<ProblemImportImage> images,
        String upstreamUrl,
        String remoteJudge,
        String remoteId
) {
    public ProblemImportDraft {
        samples = List.copyOf(samples);
        tests = List.copyOf(tests);
        codeResources = List.copyOf(codeResources);
        images = List.copyOf(images);
    }
}
