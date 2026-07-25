package com.zephyr.croj.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.model.entity.ProblemVersion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemVersionPublicationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ProblemVersionPublicationService(JdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper());
    }

    @Autowired
    public ProblemVersionPublicationService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publish(long problemId, long versionId) {
        lockProblem(problemId);
        List<PublicationCandidate> candidates = jdbc.query(
                """
                SELECT pv.state,pv.statement_json,pv.limits_json,
                       pv.judge_config_json,pv.projection_complete,tb.manifest_json
                FROM t_problem_version pv
                JOIN t_problem p ON p.id=pv.problem_id AND p.is_deleted=0
                JOIN t_test_bundle tb ON tb.problem_version_id=pv.id
                WHERE pv.id=? AND pv.problem_id=?
                FOR UPDATE
                """,
                (result, row) -> new PublicationCandidate(
                        result.getString("state"),
                        result.getString("statement_json"),
                        result.getString("limits_json"),
                        result.getString("judge_config_json"),
                        result.getBoolean("projection_complete"),
                        result.getString("manifest_json")),
                versionId,
                problemId);
        if (candidates.isEmpty()
                || (!"DRAFT".equals(candidates.get(0).state())
                        && !"PUBLISHED".equals(candidates.get(0).state()))) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
        }
        assertV1Compatible(candidates.get(0));
        if ("DRAFT".equals(candidates.get(0).state())
                && jdbc.update(
                                "UPDATE t_problem_version SET state='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE id=? AND state='DRAFT'",
                                versionId)
                        != 1) {
            throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
        }
        switchVisibleTags(problemId, candidates.get(0));
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

    private void assertV1Compatible(PublicationCandidate candidate) {
        ProblemVersion version = new ProblemVersion();
        version.setStatementJson(candidate.statementJson());
        version.setLimitsJson(candidate.limitsJson());
        version.setJudgeConfigJson(candidate.judgeConfigJson());
        version.setProjectionComplete(candidate.projectionComplete());
        try {
            new TestBundleV1Contract(objectMapper)
                    .validateAndCanonicalize(
                            version,
                            candidate.manifestJson(),
                            TestBundleV1Contract.MAX_V1_CASES);
        } catch (TestBundleV1Contract.ContractViolation exception) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
        }
    }

    private void switchVisibleTags(long problemId, PublicationCandidate candidate) {
        List<Long> tagIds = snapshotTagIds(candidate.statementJson());
        jdbc.update("DELETE FROM t_problem_tag_relation WHERE problem_id=?", problemId);
        for (Long tagId : tagIds) {
            if (jdbc.update(
                            "INSERT INTO t_problem_tag_relation(problem_id,tag_id) VALUES (?,?)",
                            problemId,
                            tagId)
                    != 1) {
                throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
            }
        }
    }

    private List<Long> snapshotTagIds(String statementJson) {
        try {
            var tags = objectMapper.readTree(statementJson).get("tags");
            if (tags == null || !tags.isArray()) {
                throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
            }
            List<Long> result = new ArrayList<>();
            Set<Long> unique = new HashSet<>();
            for (var tag : tags) {
                var id = tag.get("id");
                var name = tag.get("name");
                var color = tag.get("color");
                if (!tag.isObject()
                        || id == null
                        || !id.isIntegralNumber()
                        || !id.canConvertToLong()
                        || name == null
                        || !name.isTextual()
                        || color == null
                        || !color.isTextual()
                        || !unique.add(id.longValue())) {
                    throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
                }
                result.add(id.longValue());
            }
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | NullPointerException exception) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
        }
    }

    record PublicationCandidate(
            String state,
            String statementJson,
            String limitsJson,
            String judgeConfigJson,
            boolean projectionComplete,
            String manifestJson) {}
}
