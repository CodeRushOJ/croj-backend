package com.zephyr.croj.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.zephyr.croj.model.dto.ProblemQueryDTO;
import com.zephyr.croj.model.vo.ProblemListItemVO;
import com.zephyr.croj.model.vo.ProblemVO;
import org.apache.ibatis.annotations.Select;
import java.nio.charset.StandardCharsets;
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
        when(problems.selectForUpdate(11L)).thenReturn(published);
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
        assertEquals("Two Sum revised", updated.getValue().getTitle());
        assertEquals(0, updated.getValue().getStatus());
        assertEquals(23L, updated.getValue().getPublishedVersionId());
        ArgumentCaptor<ProblemVersion> replacement = ArgumentCaptor.forClass(ProblemVersion.class);
        verify(versions).insert(replacement.capture());
        assertEquals("DRAFT", replacement.getValue().getState());
        assertEquals(2, replacement.getValue().getVersionNo());
        assertEquals(true, replacement.getValue().getStatementJson().contains("Two Sum revised"));
        assertEquals(true, replacement.getValue().getJudgeConfigJson().contains("\"difficulty\":1"));
    }

    @Test
    void publicReadersSeeThePublishedSnapshotWhileAnEditedDraftRemainsPrivate() {
        User reader = new User();
        reader.setId(7L);
        reader.setRole(0);
        when(users.getById(7L)).thenReturn(reader);
        Problem editedDraft = problem(11L, 0, 23L);
        editedDraft.setTitle("Draft title");
        editedDraft.setDescription("Draft statement");
        editedDraft.setTimeLimit(2200);
        editedDraft.setMemoryLimit(512);
        editedDraft.setDifficulty(3);
        editedDraft.setJudgeMode(1);
        editedDraft.setTotalScore(300);
        when(problems.selectById(11L)).thenReturn(editedDraft);
        when(versions.selectById(23L)).thenReturn(publishedVersion(23L, "Published title", 1000, 256, 1, 0, 100));
        when(problems.getUserSubmitStatus(11L, 7L)).thenReturn(1);
        User administrator = new User();
        administrator.setId(9L);
        administrator.setRole(1);
        when(users.getById(9L)).thenReturn(administrator);

        ProblemVO view = service.getProblemById(11L, 7L);
        ProblemVO administratorView = service.getProblemById(11L, 9L);
        when(problems.getProblemByNo("P1000")).thenReturn(editedDraft);
        ProblemVO byNumber = service.getProblemByNo("P1000", 7L);

        assertEquals("Published title", view.getTitle());
        assertEquals("Published statement", view.getDescription());
        assertEquals(1000, view.getTimeLimit());
        assertEquals(256, view.getMemoryLimit());
        assertEquals(1, view.getDifficulty());
        assertEquals(0, view.getJudgeMode());
        assertEquals(100, view.getTotalScore());
        assertEquals(1, view.getUserStatus());
        assertEquals("Published title", byNumber.getTitle());
        assertEquals(1, byNumber.getDifficulty());
        assertEquals("Draft title", administratorView.getTitle());
        assertEquals(2200, administratorView.getTimeLimit());
        assertEquals(3, administratorView.getDifficulty());
    }

    @Test
    void publicListUsesPublishedSnapshotsAndDoesNotReturnAnotherUsersPrivateDraft() {
        User reader = new User();
        reader.setId(7L);
        reader.setRole(0);
        when(users.getById(7L)).thenReturn(reader);
        ProblemVO publicDraft = new ProblemVO();
        publicDraft.setId(11L);
        publicDraft.setTitle("Draft title");
        publicDraft.setDifficulty(3);
        publicDraft.setStatus(0);
        publicDraft.setCreateUserId(8L);
        publicDraft.setPublishedVersionId(23L);
        publicDraft.setSubmitCount(0);
        publicDraft.setAcceptedCount(0);
        Page<ProblemVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(publicDraft));
        when(problems.getProblemList(
                        any(Page.class), eq(null), eq(null), eq(null), anyList(), eq(7L), eq(false)))
                .thenReturn(page);
        when(versions.selectBatchIds(anyList()))
                .thenReturn(List.of(publishedVersion(23L, "Published title", 1000, 256, 1, 0, 100)));
        ProblemQueryDTO query = new ProblemQueryDTO();
        query.setCurrent(1);
        query.setSize(20);
        query.setTagIds(List.of());

        IPage<ProblemListItemVO> result = service.getProblemList(query, 7L);

        assertEquals(1, result.getRecords().size());
        assertEquals("Published title", result.getRecords().get(0).getTitle());
        assertEquals(1, result.getRecords().get(0).getDifficulty());
        verify(problems).getProblemList(
                any(Page.class), eq(null), eq(null), eq(null), anyList(), eq(7L), eq(false));
    }

    @Test
    void problemEditsExposeAnAggregateForUpdateMapperContract() throws Exception {
        Select lock = ProblemMapper.class
                .getMethod("selectForUpdate", Long.class)
                .getAnnotation(Select.class);

        assertEquals(true, String.join(" ", lock.value()).contains("FOR UPDATE"));
    }

    @Test
    void problemListMapperFiltersPrivateDraftsBeforePagination() throws Exception {
        String mapper = new String(
                getClass().getResourceAsStream("/mapper/ProblemMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertEquals(true, mapper.contains("p.status = 0 OR p.create_user_id = #{userId}"));
        assertEquals(
                true,
                occurrences(mapper, "JSON_EXTRACT(pv.statement_json, '$.title')") >= 2);
        assertEquals(
                true,
                occurrences(mapper, "JSON_EXTRACT(pv.judge_config_json, '$.difficulty')") >= 2);
    }

    @Test
    void problemDetailsUseTheSubmissionStatusMapperContract() throws Exception {
        Select statusQuery = ProblemMapper.class
                .getMethod("getUserSubmitStatus", Long.class, Long.class)
                .getAnnotation(Select.class);

        String sql = String.join(" ", statusQuery.value());
        assertEquals(true, sql.contains("t_submission"));
        assertEquals(true, sql.contains("status = 1"));
    }

    private int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
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

    private ProblemVersion publishedVersion(
            long id,
            String title,
            int timeLimit,
            int memoryLimit,
            int difficulty,
            int judgeMode,
            int totalScore) {
        ProblemVersion version = new ProblemVersion();
        version.setId(id);
        version.setProblemId(11L);
        version.setState("PUBLISHED");
        version.setStatementJson("""
                {"title":"%s","description":"Published statement","inputDescription":"Published input",
                 "outputDescription":"Published output","hints":[],"samples":[],"source":"published-source"}
                """.formatted(title));
        version.setLimitsJson("""
                {"timeLimit":%d,"memoryLimit":%d,"totalScore":%d}
                """.formatted(timeLimit, memoryLimit, totalScore));
        version.setJudgeConfigJson("""
                {"specialJudge":false,"specialJudgeCode":null,"specialJudgeLanguage":null,
                 "judgeMode":%d,"difficulty":%d}
                """.formatted(judgeMode, difficulty));
        return version;
    }
}
