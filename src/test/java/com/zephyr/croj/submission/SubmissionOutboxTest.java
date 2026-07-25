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

import com.zephyr.croj.model.dto.SubmissionDTO;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.outbox.SubmissionOutbox;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.service.impl.SubmissionServiceImpl;
import org.junit.jupiter.api.Test;

class SubmissionOutboxTest {

    @Test
    void submissionAndQueueRequestAreRecordedThroughTheSameTransactionalService() {
        UserService users = mock(UserService.class);
        ProblemService problems = mock(ProblemService.class);
        SubmissionOutbox outbox = mock(SubmissionOutbox.class);
        SubmissionServiceImpl service = org.mockito.Mockito.spy(
                new SubmissionServiceImpl(users, problems, outbox));

        User user = new User();
        user.setId(7L);
        Problem problem = new Problem();
        problem.setId(42L);
        problem.setStatus(0);
        when(users.getById(7L)).thenReturn(user);
        when(problems.getById(42L)).thenReturn(problem);
        when(problems.incrementSubmitCount(42L)).thenReturn(true);
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
        verify(outbox).enqueue(any(Submission.class));
    }

    @Test
    void failedCounterUpdateDoesNotEnqueueTheSubmission() {
        UserService users = mock(UserService.class);
        ProblemService problems = mock(ProblemService.class);
        SubmissionOutbox outbox = mock(SubmissionOutbox.class);
        SubmissionServiceImpl service = org.mockito.Mockito.spy(
                new SubmissionServiceImpl(users, problems, outbox));
        User user = new User();
        Problem problem = new Problem();
        problem.setStatus(0);
        when(users.getById(7L)).thenReturn(user);
        when(problems.getById(42L)).thenReturn(problem);
        when(problems.incrementSubmitCount(42L)).thenReturn(false);
        doReturn(true).when(service).save(any(Submission.class));
        SubmissionDTO request = new SubmissionDTO();
        request.setProblemId(42L);
        request.setLanguage("java17");
        request.setCode("class Main {}\n");

        assertThrows(RuntimeException.class, () -> service.submitCode(request, 7L));
        verifyNoInteractions(outbox);
    }
}
