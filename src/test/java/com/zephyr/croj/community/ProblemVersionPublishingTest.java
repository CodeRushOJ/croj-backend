package com.zephyr.croj.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.mapper.ProblemMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.dto.ProblemCreateDTO;
import com.zephyr.croj.model.dto.ProblemUpdateDTO;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.service.ProblemTagService;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.service.impl.ProblemServiceImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProblemVersionPublishingTest {
    @Mock private ProblemMapper problems;
    @Mock private ProblemVersionMapper versions;
    @Mock private ProblemTagService tags;
    @Mock private UserService users;
    private ProblemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProblemServiceImpl(tags, users, versions, new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", problems);
    }

    @Test
    @SuppressWarnings("unchecked")
    void creatingAProblemKeepsTheVersionPrivateUntilATestBundleIsAttached() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole(1);
        when(users.getById(2L)).thenReturn(admin);
        when(problems.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(problems.insert(any(Problem.class))).thenAnswer(invocation -> {
            invocation.<Problem>getArgument(0).setId(11L);
            return 1;
        });
        when(versions.insert(any(ProblemVersion.class))).thenAnswer(invocation -> {
            invocation.<ProblemVersion>getArgument(0).setId(23L);
            return 1;
        });
        ProblemCreateDTO request = new ProblemCreateDTO();
        request.setTitle("Two Sum");
        request.setDescription("Find two values.");
        request.setInputDescription("An array.");
        request.setOutputDescription("Two indices.");
        request.setHints(List.of("Use a map"));
        request.setSamples(List.of(Map.of("input", "2 7", "output", "0 1")));
        request.setTimeLimit(1000);
        request.setMemoryLimit(256);
        request.setDifficulty(1);
        request.setJudgeMode(0);
        request.setStatus(0);

        assertEquals(11L, service.createProblem(request, 2L));

        ArgumentCaptor<ProblemVersion> version = ArgumentCaptor.forClass(ProblemVersion.class);
        verify(versions).insert(version.capture());
        assertEquals("DRAFT", version.getValue().getState());
        assertEquals(1, version.getValue().getVersionNo());
        assertEquals(11L, version.getValue().getProblemId());
        ArgumentCaptor<Problem> insertedProblem = ArgumentCaptor.forClass(Problem.class);
        verify(problems).insert(insertedProblem.capture());
        assertEquals(1, insertedProblem.getValue().getStatus());
        assertNull(insertedProblem.getValue().getPublishedVersionId());
        verify(problems, never()).updateById(any(Problem.class));
    }

    @Test
    void editingAPublishedProblemKeepsTheStableVersionOnlineUntilReplacementPublishes() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole(1);
        when(users.getById(2L)).thenReturn(admin);
        Problem published = problem(11L, 0, 23L);
        when(problems.selectById(11L)).thenReturn(published);
        when(problems.updateById(any(Problem.class))).thenReturn(1);
        when(versions.findLatestVersionNumber(11L)).thenReturn(1);
        when(versions.insert(any(ProblemVersion.class))).thenReturn(1);
        ProblemUpdateDTO request = new ProblemUpdateDTO();
        request.setId(11L);
        request.setTitle("Two Sum revised");
        request.setDescription("Revised statement");
        request.setInputDescription("An array");
        request.setOutputDescription("Two indices");
        request.setHints(List.of("Use a map"));
        request.setSamples(List.of(Map.of("input", "2 7", "output", "0 1")));
        request.setTimeLimit(1200);
        request.setMemoryLimit(256);
        request.setDifficulty(1);
        request.setJudgeMode(0);

        service.updateProblem(request, 2L);

        ArgumentCaptor<Problem> updated = ArgumentCaptor.forClass(Problem.class);
        verify(problems).updateById(updated.capture());
        assertEquals(0, updated.getValue().getStatus());
        assertEquals(23L, updated.getValue().getPublishedVersionId());
        ArgumentCaptor<ProblemVersion> replacement = ArgumentCaptor.forClass(ProblemVersion.class);
        verify(versions).insert(replacement.capture());
        assertEquals("DRAFT", replacement.getValue().getState());
        assertEquals(2, replacement.getValue().getVersionNo());
    }

    private Problem problem(long id, int status, Long publishedVersionId) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setProblemNo("P1000");
        problem.setTitle("Two Sum");
        problem.setDescription("Statement");
        problem.setInputDescription("Input");
        problem.setOutputDescription("Output");
        problem.setHints(List.of());
        problem.setSamples(List.of(Map.of("input", "1 2", "output", "3")));
        problem.setTimeLimit(1000);
        problem.setMemoryLimit(256);
        problem.setDifficulty(1);
        problem.setJudgeMode(0);
        problem.setTotalScore(100);
        problem.setStatus(status);
        problem.setPublishedVersionId(publishedVersionId);
        return problem;
    }
}
