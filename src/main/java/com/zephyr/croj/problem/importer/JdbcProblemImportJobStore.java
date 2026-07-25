package com.zephyr.croj.problem.importer;

import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcProblemImportJobStore implements ProblemImportJobStore {
    private final JdbcTemplate jdbc;

    @Override
    public void create(ProblemImportJob job) {
        if (jdbc.update("""
                INSERT INTO t_problem_import_job
                  (id,actor_id,status,detected_format,file_sha256,staging_object_key,summary_json,
                   imported_count,created_at,updated_at,expires_at)
                VALUES (?,?,?,?,?,?,?,0,?,?,?)
                """,
                job.id(), job.actorId(), job.status(), job.detectedFormat(), job.fileSha256(),
                job.stagingObjectKey(), job.summaryJson(), Timestamp.from(job.createdAt()),
                Timestamp.from(job.createdAt()), Timestamp.from(job.expiresAt())) != 1) {
            throw new BusinessException(ResultCodeEnum.CREATE_ERROR);
        }
    }

    @Override
    public ProblemImportJob lockOwned(String jobId, long actorId, Instant now) {
        List<ProblemImportJob> found = jdbc.query("""
                SELECT id,actor_id,status,detected_format,file_sha256,staging_object_key,summary_json,
                       imported_count,created_at,expires_at,committed_at
                FROM t_problem_import_job
                WHERE id=? AND actor_id=? AND expires_at>?
                FOR UPDATE
                """,
                (result, row) -> new ProblemImportJob(
                        result.getString("id"),
                        result.getLong("actor_id"),
                        result.getString("status"),
                        result.getString("detected_format"),
                        result.getString("file_sha256"),
                        result.getString("staging_object_key"),
                        result.getString("summary_json"),
                        result.getInt("imported_count"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("expires_at").toInstant(),
                        result.getTimestamp("committed_at") == null
                                ? null : result.getTimestamp("committed_at").toInstant()),
                jobId, actorId, Timestamp.from(now));
        if (found.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        return found.get(0);
    }

    @Override
    public void markCommitted(String jobId, int importedCount, Instant committedAt) {
        if (jdbc.update("""
                UPDATE t_problem_import_job
                SET status='COMMITTED',imported_count=?,committed_at=?,updated_at=?
                WHERE id=? AND status='VALIDATED'
                """,
                importedCount,
                Timestamp.from(committedAt),
                Timestamp.from(committedAt),
                jobId) != 1) {
            throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
        }
    }
}
