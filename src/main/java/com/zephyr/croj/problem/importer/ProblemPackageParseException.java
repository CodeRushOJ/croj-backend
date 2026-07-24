package com.zephyr.croj.problem.importer;

public class ProblemPackageParseException extends RuntimeException {
    public ProblemPackageParseException(String message) {
        super(message);
    }

    public ProblemPackageParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
