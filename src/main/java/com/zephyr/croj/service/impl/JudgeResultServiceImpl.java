package com.zephyr.croj.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.common.enums.JudgeResultStatus;
import com.zephyr.croj.common.enums.SubmissionStatusEnum;
import com.zephyr.croj.common.exception.JudgeResultConflictException;
import com.zephyr.croj.mapper.JudgeAttemptMapper;
import com.zephyr.croj.mapper.JudgeResultReceiptMapper;
import com.zephyr.croj.mapper.ProblemMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.mapper.SubmissionMapper;
import com.zephyr.croj.model.dto.JudgeResultRequest;
import com.zephyr.croj.model.entity.JudgeResultReceipt;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.vo.JudgeResultResponse;
import com.zephyr.croj.service.JudgeResultService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JudgeResultServiceImpl implements JudgeResultService {
    private static final int MAX_ERROR_MESSAGE = 16_000;

    private final SubmissionMapper submissions;
    private final JudgeAttemptMapper attempts;
    private final JudgeResultReceiptMapper receipts;
    private final ProblemMapper problems;
    private final ProblemVersionMapper versions;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JudgeResultResponse ingest(JudgeResultRequest request) {
        JudgeResultStatus status = validate(request);
        String resultJson = canonicalPayload(request, status);
        String payloadHash = sha256(resultJson);
        JudgeResultReceipt receipt = new JudgeResultReceipt();
        receipt.setResultId(request.getResultId());
        receipt.setSubmissionId(request.getSubmissionId());
        receipt.setAttemptNo(request.getAttemptNo());
        receipt.setPayloadSha256(payloadHash);
        receipt.setFinalStatus(status.name());

        if (receipts.insertIgnore(receipt) == 0) {
            JudgeResultReceipt existing = receipts.selectById(request.getResultId());
            if (existing != null
                    && request.getSubmissionId().equals(existing.getSubmissionId())
                    && request.getAttemptNo().equals(existing.getAttemptNo())
                    && constantTimeEquals(payloadHash, existing.getPayloadSha256())) {
                return new JudgeResultResponse("DUPLICATE");
            }
            throw new JudgeResultConflictException("resultId was already used for a different payload");
        }

        Submission submission = submissions.selectById(request.getSubmissionId());
        if (submission == null || !SubmissionStatusEnum.PENDING.getCode().equals(submission.getStatus())) {
            throw new JudgeResultConflictException("submission is missing or already terminal");
        }
        validateScoreContract(request, status, submission);
        if (attempts.completeAttempt(
                request.getSubmissionId(), request.getAttemptNo(), status.name(), resultJson) != 1) {
            throw new JudgeResultConflictException("judge attempt is missing, stale, or already terminal");
        }
        if (submissions.completePending(
                request.getSubmissionId(),
                status.submissionCode(),
                request.getTimeUsedMillis(),
                request.getMemoryUsedKb(),
                request.getScore(),
                resultJson,
                errorMessage(request, status)) != 1) {
            throw new JudgeResultConflictException("submission terminal state won the update race");
        }
        if (status == JudgeResultStatus.ACCEPTED
                && problems.incrementAcceptedCount(submission.getProblemId()) != 1) {
            throw new IllegalStateException("failed to update accepted submission counter");
        }
        return new JudgeResultResponse("APPLIED");
    }

    private JudgeResultStatus validate(JudgeResultRequest request) {
        JudgeResultStatus status = JudgeResultStatus.parse(request.getStatus());
        if (status == JudgeResultStatus.ACCEPTED && request.getExitCode() != 0) {
            throw new JudgeResultConflictException("ACCEPTED requires exitCode=0");
        }
        if (status == JudgeResultStatus.COMPILE_ERROR
                && (request.getCompileError() == null || request.getCompileError().isBlank())) {
            throw new JudgeResultConflictException("COMPILE_ERROR requires compileError");
        }
        if ((request.getScore() == null) != (request.getTotalScore() == null)) {
            throw new JudgeResultConflictException("score and totalScore must be present together");
        }
        if (request.getScore() != null
                && (request.getScore() < 0
                        || request.getTotalScore() <= 0
                        || request.getScore() > request.getTotalScore()
                        || request.getTotalScore() > 1_000_000_000)) {
            throw new JudgeResultConflictException("score is outside the judge result contract");
        }
        return status;
    }

    private void validateScoreContract(
            JudgeResultRequest request, JudgeResultStatus status, Submission submission) {
        if (submission.getProblemVersionId() == null) {
            throw new JudgeResultConflictException("submission has no immutable problem version");
        }
        ProblemVersion version = versions.selectById(submission.getProblemVersionId());
        if (version == null
                || !submission.getProblemId().equals(version.getProblemId())
                || !"PUBLISHED".equals(version.getState())) {
            throw new JudgeResultConflictException("immutable problem version is unavailable");
        }
        try {
            var limits = objectMapper.readTree(version.getLimitsJson());
            var judge = objectMapper.readTree(version.getJudgeConfigJson());
            if (limits == null
                    || !limits.isObject()
                    || judge == null
                    || !judge.isObject()
                    || !judge.path("judgeMode").isIntegralNumber()) {
                throw new JudgeResultConflictException("immutable judge score config is invalid");
            }
            int judgeMode = judge.path("judgeMode").intValue();
            if (judgeMode == 0) {
                if (request.getScore() != null) {
                    throw new JudgeResultConflictException("ACM result must not contain a score");
                }
                return;
            }
            if (judgeMode != 1
                    || !limits.path("totalScore").isIntegralNumber()
                    || limits.path("totalScore").intValue() <= 0) {
                throw new JudgeResultConflictException("immutable judge score config is invalid");
            }
            int immutableTotal = limits.path("totalScore").intValue();
            if (status == JudgeResultStatus.SYSTEM_ERROR && request.getScore() == null) {
                return;
            }
            if (request.getScore() == null
                    || !Integer.valueOf(immutableTotal).equals(request.getTotalScore())) {
                throw new JudgeResultConflictException(
                        "OI result totalScore disagrees with the immutable problem version");
            }
            boolean fullScore = request.getScore().equals(request.getTotalScore());
            if ((status == JudgeResultStatus.ACCEPTED) != fullScore) {
                throw new JudgeResultConflictException(
                        "OI ACCEPTED status must exactly match a full score");
            }
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new JudgeResultConflictException("immutable judge score config is invalid");
        }
    }

    private String canonicalPayload(JudgeResultRequest request, JudgeResultStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultId", request.getResultId());
        payload.put("submissionId", request.getSubmissionId());
        payload.put("attemptNo", request.getAttemptNo());
        payload.put("status", status.name());
        payload.put("exitCode", request.getExitCode());
        payload.put("timeUsedMillis", request.getTimeUsedMillis());
        payload.put("memoryUsedKb", request.getMemoryUsedKb());
        if (request.getScore() != null) {
            payload.put("score", request.getScore());
            payload.put("totalScore", request.getTotalScore());
        }
        payload.put("stdout", nullToEmpty(request.getStdout()));
        payload.put("stderr", nullToEmpty(request.getStderr()));
        payload.put("compileError", nullToEmpty(request.getCompileError()));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize judge result", exception);
        }
    }

    private String errorMessage(JudgeResultRequest request, JudgeResultStatus status) {
        if (status == JudgeResultStatus.ACCEPTED || status == JudgeResultStatus.WRONG_ANSWER) {
            return "";
        }
        String value = status == JudgeResultStatus.COMPILE_ERROR
                ? nullToEmpty(request.getCompileError())
                : nullToEmpty(request.getStderr());
        return value.substring(0, Math.min(value.length(), MAX_ERROR_MESSAGE));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
