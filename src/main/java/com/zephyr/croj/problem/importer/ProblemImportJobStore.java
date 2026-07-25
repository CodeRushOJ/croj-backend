package com.zephyr.croj.problem.importer;

import java.time.Instant;

public interface ProblemImportJobStore {
    void create(ProblemImportJob job);

    ProblemImportJob lockOwned(String jobId, long actorId, Instant now);

    void markCommitted(String jobId, int importedCount, Instant committedAt);
}
