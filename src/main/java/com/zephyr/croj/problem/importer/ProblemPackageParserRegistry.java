package com.zephyr.croj.problem.importer;

import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProblemPackageParserRegistry {
    private final Map<ProblemPackageFormat, ProblemPackageParser> parsers;

    public ProblemPackageParserRegistry(List<ProblemPackageParser> parsers) {
        if (parsers == null || parsers.isEmpty()) {
            throw new IllegalArgumentException("At least one problem package parser is required");
        }
        EnumMap<ProblemPackageFormat, ProblemPackageParser> registered =
                new EnumMap<>(ProblemPackageFormat.class);
        for (ProblemPackageParser parser : parsers) {
            if (parser == null || parser.format() == null) {
                throw new IllegalArgumentException("Problem package parser and format are required");
            }
            if (registered.putIfAbsent(parser.format(), parser) != null) {
                throw new IllegalArgumentException("Duplicate problem package parser: " + parser.format());
            }
        }
        this.parsers = Collections.unmodifiableMap(registered);
    }

    public Set<ProblemPackageFormat> supportedFormats() {
        return parsers.keySet();
    }

    public ProblemImportBatch parse(ProblemPackageFormat format, InputStream input) {
        ProblemPackageParser parser = parsers.get(format);
        if (parser == null) {
            throw new ProblemPackageParseException("Unsupported problem package format: " + format);
        }
        return parser.parse(input);
    }
}
