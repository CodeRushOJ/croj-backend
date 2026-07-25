package com.zephyr.croj.submission;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.contest.ContestService;
import com.zephyr.croj.mapper.JudgeAttemptMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.mapper.SubmissionMapper;
import com.zephyr.croj.model.dto.SubmissionQueryDTO;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.outbox.SubmissionOutbox;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.service.impl.SubmissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionVisibilityPolicyTest {

    @Mock private UserService users;
    @Mock private ProblemService problems;
    @Mock private SubmissionOutbox outbox;
    @Mock private JudgeAttemptMapper attempts;
    @Mock private ContestService contests;
    @Mock private ProblemVersionMapper versions;
    @Mock private SubmissionMapper submissions;

    private SubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubmissionServiceImpl(users, problems, outbox, attempts, contests, versions);
        ReflectionTestUtils.setField(service, "baseMapper", submissions);
    }

    @Test
    void ordinaryUserOmittingUserFilterIsStillRestrictedToSelf() {
        long currentUserId = 7L;
        when(users.getById(currentUserId)).thenReturn(user(currentUserId, 0));
        when(submissions.getSubmissionList(any(), eq(currentUserId), any(), any(), any()))
                .thenReturn(new Page<>());

        service.getSubmissionList(new SubmissionQueryDTO(), currentUserId);

        verify(submissions).getSubmissionList(any(), eq(currentUserId), any(), any(), any());
    }

    @Test
    void ordinaryUserCannotReadAnotherUsersSubmissionDetails() {
        long currentUserId = 7L;
        Submission foreign = new Submission();
        foreign.setId(99L);
        foreign.setUserId(8L);
        foreign.setContestId(3L);
        foreign.setStatus(1);
        when(submissions.selectById(99L)).thenReturn(foreign);
        when(users.getById(currentUserId)).thenReturn(user(currentUserId, 0));

        assertThrows(BusinessException.class, () -> service.getSubmissionById(99L, currentUserId));
    }

    private User user(long id, int role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
