package com.zephyr.croj.problem;

import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProblemVersionPublicationService {
    private final JdbcTemplate jdbc;

    @Transactional
    public void publish(long problemId, long versionId) {
        lockProblem(problemId);
        List<String> states = jdbc.query(
                """
                SELECT pv.state
                FROM t_problem_version pv
                JOIN t_problem p ON p.id=pv.problem_id AND p.is_deleted=0
                JOIN t_test_bundle tb ON tb.problem_version_id=pv.id
                WHERE pv.id=? AND pv.problem_id=?
                FOR UPDATE
                """,
                (result, row) -> result.getString("state"),
                versionId,
                problemId);
        if (states.isEmpty() || (!"DRAFT".equals(states.get(0)) && !"PUBLISHED".equals(states.get(0)))) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
        }
        if ("DRAFT".equals(states.get(0))
                && jdbc.update(
                                "UPDATE t_problem_version SET state='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE id=? AND state='DRAFT'",
                                versionId)
                        != 1) {
            throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
        }
        if (jdbc.update(
                        "UPDATE t_problem SET published_version_id=?,status=0 WHERE id=? AND is_deleted=0",
                        versionId,
                        problemId)
                != 1) {
            throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    public void lockProblem(long problemId) {
        List<Long> locked = jdbc.query(
                "SELECT id FROM t_problem WHERE id=? AND is_deleted=0 FOR UPDATE",
                (result, row) -> result.getLong("id"),
                problemId);
        if (locked.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
        }
    }
}
