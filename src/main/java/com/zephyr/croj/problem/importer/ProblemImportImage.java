package com.zephyr.croj.problem.importer;

import java.util.Arrays;

public record ProblemImportImage(String source, byte[] content) {
    public ProblemImportImage {
        content = content == null ? null : Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return content == null ? null : Arrays.copyOf(content, content.length);
    }
}
