package com.zephyr.croj.problem.importer;

public interface ProblemImportStagingStorage {
    String put(String jobId, byte[] packageBytes, String sha256);

    byte[] get(String objectKey);
}
