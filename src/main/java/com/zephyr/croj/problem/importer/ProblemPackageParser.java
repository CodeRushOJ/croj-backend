package com.zephyr.croj.problem.importer;

import java.io.InputStream;

public interface ProblemPackageParser {
    ProblemPackageFormat format();

    ProblemImportBatch parse(InputStream input);
}
