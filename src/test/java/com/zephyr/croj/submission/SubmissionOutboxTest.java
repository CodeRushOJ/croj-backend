package com.zephyr.croj.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zephyr.croj.contest.ContestService;
import com.zephyr.croj.model.dto.SubmissionDTO;
import com.zephyr.croj.mapper.JudgeAttemptMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.JudgeAttempt;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.outbox.SubmissionOutbox;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.service.impl.SubmissionServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SubmissionOutboxTest {

    @Test
    void submissionAndQueueRequestAreRecordedThroughTheSameTransactionalService() {
        UserService users = mock(UserService.class);
        ProblemService problems = mock(ProblemService.class);
        SubmissionOutbox outbox = mock(SubmissionOutbox.class);
        JudgeAttemptMapper attempts = mock(JudgeAttemptMapper.class);
        ContestService contests = mock(ContestService.class);
        ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
        when(attempts.insert(any(JudgeAttempt.class))).thenReturn(1);
        SubmissionServiceImpl service = org.mockito.Mockito.spy(
                new SubmissionServiceImpl(users, problems, outbox, attempts, contests, versions));

        User user = new User();
        user.setId(7L);
        Problem problem = new Problem();
        problem.setId(42L);
        problem.setStatus(0);
        problem.setPublishedVersionId(88L);
        when(users.getById(7L)).thenReturn(user);
        when(problems.getById(42L)).thenReturn(problem);
        when(problems.incrementSubmitCount(42L)).thenReturn(true);
        when(versions.isJudgeReady(42L, 88L)).thenReturn(true);
        doAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setId(99L);
            return true;
        }).when(service).save(any(Submission.class));

        SubmissionDTO request = new SubmissionDTO();
        request.setProblemId(42L);
        request.setLanguage("java17");
        request.setCode("class Main {}\n");

        assertEquals(99L, service.submitCode(request, 7L));
        verify(problems).incrementSubmitCount(42L);
        ArgumentCaptor<Submission> queued = ArgumentCaptor.forClass(Submission.class);
        verify(outbox).enqueue(queued.capture());
        assertEquals(88L, queued.getValue().getProblemVersionId());
    }

    @Test
    void failedCounterUpdateDoesNotEnqueueTheSubmission() {
        UserService users = mock(UserService.class);
        ProblemService problems = mock(ProblemService.class);
        SubmissionOutbox outbox = mock(SubmissionOutbox.class);
        JudgeAttemptMapper attempts = mock(JudgeAttemptMapper.class);
        ContestService contests = mock(ContestService.class);
        ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
        SubmissionServiceImpl service = org.mockito.Mockito.spy(
                new SubmissionServiceImpl(users, problems, outbox, attempts, contests, versions));
        User user = new User();
        Problem problem = new Problem();
        problem.setStatus(0);
        problem.setPublishedVersionId(88L);
        when(users.getById(7L)).thenReturn(user);
        when(problems.getById(42L)).thenReturn(problem);
        when(problems.incrementSubmitCount(42L)).thenReturn(false);
        when(versions.isJudgeReady(42L, 88L)).thenReturn(true);
        doReturn(true).when(service).save(any(Submission.class));
        SubmissionDTO request = new SubmissionDTO();
        request.setProblemId(42L);
        request.setLanguage("java17");
        request.setCode("class Main {}\n");

        assertThrows(RuntimeException.class, () -> service.submitCode(request, 7L));
        verifyNoInteractions(outbox);
    }

    @Test
    void contestSubmissionPinsTheArrangedProblemVersion() {
        UserService users = mock(UserService.class);
        ProblemService problems = mock(ProblemService.class);
        SubmissionOutbox outbox = mock(SubmissionOutbox.class);
        JudgeAttemptMapper attempts = mock(JudgeAttemptMapper.class);
        ContestService contests = mock(ContestService.class);
        ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
        when(attempts.insert(any(JudgeAttempt.class))).thenReturn(1);
        SubmissionServiceImpl service = org.mockito.Mockito.spy(
                new SubmissionServiceImpl(users, problems, outbox, attempts, contests, versions));

        User user = new User();
        user.setId(7L);
        Problem problem = new Problem();
        problem.setId(42L);
        problem.setStatus(0);
        when(users.getById(7L)).thenReturn(user);
        when(problems.getById(42L)).thenReturn(problem);
        when(problems.incrementSubmitCount(42L)).thenReturn(true);
        when(contests.validateSubmission(5L, 7L, 42L)).thenReturn(101L);
        doAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setId(99L);
            return true;
        }).when(service).save(any(Submission.class));

        SubmissionDTO request = new SubmissionDTO();
        request.setProblemId(42L);
        request.setContestId(5L);
        request.setLanguage("java17");
        request.setCode("class Main {}\n");

        assertEquals(99L, service.submitCode(request, 7L));
        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(service).save(saved.capture());
        assertEquals(5L, saved.getValue().getContestId());
        assertEquals(101L, saved.getValue().getProblemVersionId());
        verify(contests).validateSubmission(5L, 7L, 42L);
    }

    @Test
    void ordinarySubmissionRejectsProblemWithoutAJudgeReadyPublishedVersion() {
        UserService users = mock(UserService.class);
        ProblemService problems = mock(ProblemService.class);
        SubmissionOutbox outbox = mock(SubmissionOutbox.class);
        JudgeAttemptMapper attempts = mock(JudgeAttemptMapper.class);
        ContestService contests = mock(ContestService.class);
        ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
        SubmissionServiceImpl service = org.mockito.Mockito.spy(
                new SubmissionServiceImpl(users, problems, outbox, attempts, contests, versions));
        User user = new User();
        Problem problem = new Problem();
        problem.setId(42L);
        problem.setStatus(0);
        problem.setPublishedVersionId(88L);
        when(users.getById(7L)).thenReturn(user);
        when(problems.getById(42L)).thenReturn(problem);
        when(versions.isJudgeReady(42L, 88L)).thenReturn(false);
        SubmissionDTO request = new SubmissionDTO();
        request.setProblemId(42L);
        request.setLanguage("java17");
        request.setCode("class Main {}\n");

        assertThrows(RuntimeException.class, () -> service.submitCode(request, 7L));

        verifyNoInteractions(outbox);
    }
}
