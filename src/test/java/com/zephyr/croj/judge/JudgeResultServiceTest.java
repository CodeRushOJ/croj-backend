package com.zephyr.croj.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.zephyr.croj.service.impl.JudgeResultServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudgeResultServiceTest {
    @Mock private SubmissionMapper submissions;
    @Mock private JudgeAttemptMapper attempts;
    @Mock private JudgeResultReceiptMapper receipts;
    @Mock private ProblemMapper problems;
    @Mock private ProblemVersionMapper versions;
    private JudgeResultServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JudgeResultServiceImpl(
                submissions, attempts, receipts, problems, versions, new ObjectMapper());
    }

    @Test
    void appliesAnAcceptedResultExactlyOnceAndUpdatesTheProblemCounter() {
        JudgeResultRequest request = accepted("event-1");
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(versions.selectById(101L)).thenReturn(acmVersion());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(submissions.completePending(
                        anyLong(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        nullable(Integer.class),
                        anyString(),
                        anyString()))
                .thenReturn(1);
        when(problems.incrementAcceptedCount(42L)).thenReturn(1);

        assertEquals("APPLIED", service.ingest(request).disposition());
    }

    @Test
    void sameResultIdAndPayloadIsIdempotent() {
        JudgeResultRequest request = accepted("event-1");
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(versions.selectById(101L)).thenReturn(acmVersion());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(submissions.completePending(
                        anyLong(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        nullable(Integer.class),
                        anyString(),
                        anyString()))
                .thenReturn(1);
        when(problems.incrementAcceptedCount(42L)).thenReturn(1);
        assertEquals("APPLIED", service.ingest(request).disposition());
        ArgumentCaptor<JudgeResultReceipt> saved = ArgumentCaptor.forClass(JudgeResultReceipt.class);
        org.mockito.Mockito.verify(receipts).insertIgnore(saved.capture());

        when(receipts.insertIgnore(any())).thenReturn(0);
        when(receipts.selectById("event-1")).thenReturn(saved.getValue());
        assertEquals("DUPLICATE", service.ingest(request).disposition());
    }

    @Test
    void staleAttemptCannotOverwriteSubmissionState() {
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(versions.selectById(101L)).thenReturn(acmVersion());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(0);

        assertThrows(JudgeResultConflictException.class, () -> service.ingest(accepted("event-stale")));
    }

    @Test
    void persistsAValidatedPartialOIScore() {
        JudgeResultRequest request = accepted("event-oi");
        request.setStatus("WRONG_ANSWER");
        request.setScore(70);
        request.setTotalScore(100);
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(versions.selectById(101L)).thenReturn(oiVersion());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(submissions.completePending(
                        anyLong(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        nullable(Integer.class),
                        anyString(),
                        anyString()))
                .thenReturn(1);

        assertEquals("APPLIED", service.ingest(request).disposition());
        verify(submissions)
                .completePending(
                        eq(99L),
                        eq(3),
                        eq(12),
                        eq(2048),
                        eq(70),
                        org.mockito.ArgumentMatchers.contains("\"score\":70"),
                        eq(""));
    }

    @Test
    void acceptsScorelessInfrastructureFailureForAnOISubmission() {
        JudgeResultRequest request = accepted("event-oi-system");
        request.setStatus("SYSTEM_ERROR");
        request.setExitCode(-1);
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(versions.selectById(101L)).thenReturn(oiVersion());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(submissions.completePending(
                        anyLong(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        nullable(Integer.class),
                        anyString(),
                        anyString()))
                .thenReturn(1);

        assertEquals("APPLIED", service.ingest(request).disposition());
        verify(submissions)
                .completePending(
                        eq(99L),
                        eq(7),
                        eq(12),
                        eq(2048),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.argThat(
                                value -> value != null && !value.contains("\"score\"")),
                        anyString());
    }

    @Test
    void persistsOutputLimitExceededAsTheNativeTerminalStatus() {
        JudgeResultRequest request = accepted("event-ole");
        request.setStatus("OUTPUT_LIMIT_EXCEEDED");
        request.setExitCode(-1);
        request.setStderr("captured output exceeded the configured limit");
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(versions.selectById(101L)).thenReturn(acmVersion());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(submissions.completePending(
                        anyLong(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        nullable(Integer.class),
                        anyString(),
                        anyString()))
                .thenReturn(1);

        assertEquals("APPLIED", service.ingest(request).disposition());
        verify(submissions)
                .completePending(
                        eq(99L),
                        eq(8),
                        eq(12),
                        eq(2048),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.contains("\"status\":\"OUTPUT_LIMIT_EXCEEDED\""),
                        eq("captured output exceeded the configured limit"));
    }

    @Test
    void rejectsScoresThatDisagreeWithTheImmutableJudgeMode() {
        JudgeResultRequest request = accepted("event-invalid-score");
        request.setScore(70);
        request.setTotalScore(100);
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(versions.selectById(101L)).thenReturn(acmVersion());

        assertThrows(JudgeResultConflictException.class, () -> service.ingest(request));
    }

    private JudgeResultRequest accepted(String resultId) {
        JudgeResultRequest request = new JudgeResultRequest();
        request.setResultId(resultId);
        request.setSubmissionId(99L);
        request.setAttemptNo(1);
        request.setStatus("ACCEPTED");
        request.setExitCode(0);
        request.setTimeUsedMillis(12);
        request.setMemoryUsedKb(2048);
        request.setStdout("ok\n");
        request.setStderr("");
        request.setCompileError("");
        return request;
    }

    private Submission pendingSubmission() {
        Submission submission = new Submission();
        submission.setId(99L);
        submission.setProblemId(42L);
        submission.setProblemVersionId(101L);
        submission.setStatus(0);
        return submission;
    }

    private ProblemVersion acmVersion() {
        return version(0);
    }

    private ProblemVersion oiVersion() {
        return version(1);
    }

    private ProblemVersion version(int judgeMode) {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("PUBLISHED");
        version.setLimitsJson("{\"timeLimit\":1000,\"memoryLimit\":64,\"totalScore\":100}");
        version.setJudgeConfigJson(
                "{\"specialJudge\":false,\"specialJudgeCode\":null,"
                        + "\"specialJudgeLanguage\":null,\"judgeMode\":"
                        + judgeMode
                        + "}");
        return version;
    }
}
