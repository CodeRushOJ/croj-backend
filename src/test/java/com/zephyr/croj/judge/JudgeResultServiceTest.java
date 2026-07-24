package com.zephyr.croj.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.common.exception.JudgeResultConflictException;
import com.zephyr.croj.mapper.JudgeAttemptMapper;
import com.zephyr.croj.mapper.JudgeResultReceiptMapper;
import com.zephyr.croj.mapper.ProblemMapper;
import com.zephyr.croj.mapper.SubmissionMapper;
import com.zephyr.croj.model.dto.JudgeResultRequest;
import com.zephyr.croj.model.entity.JudgeResultReceipt;
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
    private JudgeResultServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JudgeResultServiceImpl(submissions, attempts, receipts, problems, new ObjectMapper());
    }

    @Test
    void appliesAnAcceptedResultExactlyOnceAndUpdatesTheProblemCounter() {
        JudgeResultRequest request = accepted("event-1");
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(submissions.completePending(anyLong(), anyInt(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(1);
        when(problems.incrementAcceptedCount(42L)).thenReturn(1);

        assertEquals("APPLIED", service.ingest(request).disposition());
    }

    @Test
    void sameResultIdAndPayloadIsIdempotent() {
        JudgeResultRequest request = accepted("event-1");
        when(receipts.insertIgnore(any())).thenReturn(1);
        when(submissions.selectById(99L)).thenReturn(pendingSubmission());
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(submissions.completePending(anyLong(), anyInt(), anyInt(), anyInt(), anyString(), anyString()))
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
        when(attempts.completeAttempt(anyLong(), anyInt(), anyString(), anyString())).thenReturn(0);

        assertThrows(JudgeResultConflictException.class, () -> service.ingest(accepted("event-stale")));
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
        submission.setStatus(0);
        return submission;
    }
}
