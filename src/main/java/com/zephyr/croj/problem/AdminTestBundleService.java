package com.zephyr.croj.problem;

import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-bundle", name = "enabled", havingValue = "true")
public class AdminTestBundleService {
    private final JdbcTemplate jdbc;
    private final TestBundleService bundles;
    private final ProblemVersionPublicationService publications;

    @Transactional(readOnly = true)
    public View describe(long problemId, long versionId) {
        return load(problemId, versionId, false);
    }

    @Transactional
    public View upload(long problemId, long versionId, String ifMatch, byte[] archive) {
        View current = load(problemId, versionId, true);
        assertPrecondition(ifMatch, current.etag());
        if (!"DRAFT".equals(current.state())) {
            throw TestBundleApiException.conflict("only a draft problem version accepts a test bundle");
        }
        try {
            bundles.attach(problemId, versionId, archive);
        } catch (BusinessException exception) {
            if (ResultCodeEnum.PARAM_ERROR.getCode().equals(exception.getCode())) {
                throw TestBundleApiException.unprocessable();
            }
            if (ResultCodeEnum.PROBLEM_NOT_JUDGE_READY.getCode().equals(exception.getCode())) {
                throw TestBundleApiException.conflict("only a draft problem version accepts a test bundle");
            }
            throw exception;
        }
        return load(problemId, versionId, false);
    }

    @Transactional
    public View publish(long problemId, long versionId, String ifMatch) {
        View current = load(problemId, versionId, true);
        assertPrecondition(ifMatch, current.etag());
        if (!current.attached()) {
            throw TestBundleApiException.conflict(
                    "a validated test bundle must be attached before publication");
        }
        try {
            publications.publish(problemId, versionId);
        } catch (BusinessException exception) {
            if (ResultCodeEnum.PROBLEM_NOT_JUDGE_READY.getCode().equals(exception.getCode())) {
                throw TestBundleApiException.conflict("problem version is not publishable");
            }
            throw exception;
        }
        return load(problemId, versionId, false);
    }

    private View load(long problemId, long versionId, boolean lock) {
        List<VersionRow> versions = jdbc.query(
                """
                SELECT pv.id,pv.problem_id,pv.version_no,pv.state
                FROM t_problem_version pv
                JOIN t_problem p ON p.id=pv.problem_id AND p.is_deleted=0
                WHERE pv.problem_id=? AND pv.id=?
                """ + (lock ? " FOR UPDATE" : ""),
                (result, row) -> new VersionRow(
                        result.getLong("problem_id"),
                        result.getLong("id"),
                        result.getInt("version_no"),
                        result.getString("state")),
                problemId,
                versionId);
        if (versions.isEmpty()) {
            throw TestBundleApiException.notFound();
        }
        VersionRow version = versions.get(0);
        List<BundleRow> bundleRows = jdbc.query(
                """
                SELECT sha256,size_bytes,manifest_json
                FROM t_test_bundle
                WHERE problem_version_id=?
                """,
                (result, row) -> new BundleRow(
                        result.getString("sha256"),
                        result.getLong("size_bytes"),
                        result.getString("manifest_json")),
                versionId);
        BundleRow bundle = bundleRows.isEmpty() ? null : bundleRows.get(0);
        String digest = bundle == null ? "none" : bundle.sha256();
        String etag = "\"tb-v1-%d-%s-%s\"".formatted(versionId, version.state(), digest);
        return new View(
                version.problemId(),
                version.versionId(),
                version.versionNo(),
                version.state(),
                bundle != null,
                bundle == null ? null : bundle.sha256(),
                bundle == null ? null : bundle.sizeBytes(),
                bundle == null ? null : bundle.manifestJson(),
                etag);
    }

    private void assertPrecondition(String ifMatch, String currentEtag) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw TestBundleApiException.preconditionRequired();
        }
        if (ifMatch.startsWith("W/")
                || "*".equals(ifMatch)
                || ifMatch.indexOf(',') >= 0
                || ifMatch.indexOf('\r') >= 0
                || ifMatch.indexOf('\n') >= 0
                || ifMatch.length() < 2
                || ifMatch.charAt(0) != '"'
                || ifMatch.charAt(ifMatch.length() - 1) != '"') {
            throw TestBundleApiException.badRequest("If-Match must contain one strong entity tag");
        }
        if (!currentEtag.equals(ifMatch)) {
            throw TestBundleApiException.preconditionFailed();
        }
    }

    private record VersionRow(long problemId, long versionId, int versionNo, String state) {}

    private record BundleRow(String sha256, long sizeBytes, String manifestJson) {}

    public record View(
            long problemId,
            long versionId,
            int versionNo,
            String state,
            boolean attached,
            String sha256,
            Long sizeBytes,
            String manifest,
            String etag) {}
}
