package com.zephyr.croj.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.mapper.ProblemMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.dto.ProblemCreateDTO;
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
    void creatingAPublicProblemAlsoPublishesAnImmutableVersion() {
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
        when(problems.updateById(any(Problem.class))).thenReturn(1);
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
        assertEquals("PUBLISHED", version.getValue().getState());
        assertEquals(1, version.getValue().getVersionNo());
        assertEquals(11L, version.getValue().getProblemId());
        ArgumentCaptor<Problem> updatedProblem = ArgumentCaptor.forClass(Problem.class);
        verify(problems).updateById(updatedProblem.capture());
        assertEquals(23L, updatedProblem.getValue().getPublishedVersionId());
    }
}
