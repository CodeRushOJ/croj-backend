package com.zephyr.croj.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.projection.ProblemTagProjection;
import com.zephyr.croj.service.ProblemTagService;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.service.impl.ProblemServiceImpl;
import com.zephyr.croj.model.dto.ProblemQueryDTO;
import com.zephyr.croj.model.vo.ProblemListItemVO;
import com.zephyr.croj.model.vo.ProblemTagVO;
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
        assertEquals(
                true,
                version.getValue().getJudgeConfigJson().contains("\"checker\":\"exact\""));
        ArgumentCaptor<Problem> insertedProblem = ArgumentCaptor.forClass(Problem.class);
        verify(problems).insert(insertedProblem.capture());
        assertEquals(1, insertedProblem.getValue().getStatus());
        assertNull(insertedProblem.getValue().getPublishedVersionId());
        verify(problems, never()).updateById(any(Problem.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void freezesTheRequestedTokenCheckerInTheProblemAndVersionSnapshot() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole(1);
        when(users.getById(2L)).thenReturn(admin);
        when(problems.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(problems.insert(any(Problem.class))).thenAnswer(invocation -> {
            invocation.<Problem>getArgument(0).setId(11L);
            return 1;
        });
        when(versions.insert(any(ProblemVersion.class))).thenReturn(1);
        ProblemCreateDTO request = createRequest();
        request.setChecker("token");

        service.createProblem(request, 2L);

        ArgumentCaptor<Problem> problem = ArgumentCaptor.forClass(Problem.class);
        verify(problems).insert(problem.capture());
        assertEquals("token", problem.getValue().getChecker());
        ArgumentCaptor<ProblemVersion> version = ArgumentCaptor.forClass(ProblemVersion.class);
        verify(versions).insert(version.capture());
        assertEquals(
                true,
                version.getValue().getJudgeConfigJson().contains("\"checker\":\"token\""));
    }

    @Test
    void rejectsSpecialCheckerWithoutAnImmutableSourceAndCanonicalLanguage() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole(1);
        when(users.getById(2L)).thenReturn(admin);
        ProblemCreateDTO request = createRequest();
        request.setChecker("special");
        request.setIsSpecialJudge(true);
        request.setSpecialJudgeLanguage("cpp");

        assertThrows(BusinessException.class, () -> service.createProblem(request, 2L));

        verify(problems, never()).insert(any(Problem.class));
        verify(versions, never()).insert(any(ProblemVersion.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestedTagsMustAllResolveBeforeTheImmutableVersionIsCreated() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole(1);
        when(users.getById(2L)).thenReturn(admin);
        when(problems.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(problems.insert(any(Problem.class))).thenAnswer(invocation -> {
            invocation.<Problem>getArgument(0).setId(11L);
            return 1;
        });
        when(tags.saveProblemTags(11L, List.of(5L, 999L))).thenReturn(true);
        ProblemTagVO resolved = new ProblemTagVO();
        resolved.setId(5L);
        resolved.setName("graphs");
        resolved.setColor("#123456");
        when(tags.getTagsByProblemId(11L)).thenReturn(List.of(resolved));
        ProblemCreateDTO request = createRequest();
        request.setTagIds(List.of(5L, 999L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.createProblem(request, 2L));

        assertEquals("problem tags contain unknown or duplicate ids", exception.getMessage());
        verify(versions, never()).insert(any(ProblemVersion.class));
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
    void disablingSpecialJudgeClearsPrivateSourceBeforeFreezingTheReplacement() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole(1);
        when(users.getById(2L)).thenReturn(admin);
        Problem special = problem(11L, 0, 23L);
        special.setIsSpecialJudge(true);
        special.setChecker("special");
        special.setSpecialJudgeCode("int main() { return 0; }");
        special.setSpecialJudgeLanguage("cpp");
        when(problems.selectForUpdate(11L)).thenReturn(special);
        when(problems.updateById(any(Problem.class))).thenReturn(1);
        when(versions.findLatestVersionNumber(11L)).thenReturn(1);
        when(versions.insert(any(ProblemVersion.class))).thenReturn(1);
        ProblemUpdateDTO request = new ProblemUpdateDTO();
        request.setId(11L);
        request.setIsSpecialJudge(false);
        request.setChecker("token");

        service.updateProblem(request, 2L);

        ArgumentCaptor<Problem> updated = ArgumentCaptor.forClass(Problem.class);
        verify(problems).updateById(updated.capture());
        assertEquals("token", updated.getValue().getChecker());
        assertNull(updated.getValue().getSpecialJudgeCode());
        assertNull(updated.getValue().getSpecialJudgeLanguage());
        ArgumentCaptor<ProblemVersion> replacement = ArgumentCaptor.forClass(ProblemVersion.class);
        verify(versions).insert(replacement.capture());
        assertEquals(
                true,
                replacement
                        .getValue()
                        .getJudgeConfigJson()
                        .contains("\"checker\":\"token\""));
        assertEquals(
                true,
                replacement
                        .getValue()
                        .getJudgeConfigJson()
                        .contains("\"specialJudgeCode\":null"));
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
        assertEquals("exact", view.getChecker());
        assertEquals(100, view.getTotalScore());
        assertEquals(1, view.getUserStatus());
        assertEquals("Published title", byNumber.getTitle());
        assertEquals(1, byNumber.getDifficulty());
        assertEquals("Draft title", administratorView.getTitle());
        assertEquals(2200, administratorView.getTimeLimit());
        assertEquals(3, administratorView.getDifficulty());
    }

    @Test
    void publicReadersSeePublishedTagSnapshotsInsteadOfEditedDraftRelations() {
        User reader = new User();
        reader.setId(7L);
        reader.setRole(0);
        when(users.getById(7L)).thenReturn(reader);
        Problem editedDraft = problem(11L, 0, 23L);
        when(problems.selectById(11L)).thenReturn(editedDraft);
        ProblemVersion published =
                publishedVersion(23L, "Published title", 1000, 256, 1, 0, 100);
        published.setStatementJson(published.getStatementJson().replace(
                "\"tags\":[]",
                "\"tags\":["
                        + "{\"id\":5,\"name\":\"published\",\"color\":\"#111111\"}]"));
        when(versions.selectById(23L)).thenReturn(published);

        ProblemVO view = service.getProblemById(11L, 7L);

        assertEquals(List.of(5L), view.getTags().stream().map(ProblemTagVO::getId).toList());
        assertEquals("published", view.getTags().get(0).getName());
        verify(tags, never()).getTagsByProblemId(11L);
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
    @SuppressWarnings("unchecked")
    void batchTagsAreGroupedByProblemIdInsteadOfTagId() {
        User administrator = new User();
        administrator.setId(9L);
        administrator.setRole(1);
        when(users.getById(9L)).thenReturn(administrator);
        ProblemVO problem = new ProblemVO();
        problem.setId(42L);
        problem.setTitle("Problem");
        problem.setDifficulty(1);
        problem.setSubmitCount(0);
        problem.setAcceptedCount(0);
        Page<ProblemVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(problem));
        when(problems.getProblemList(
                        any(Page.class), eq(null), eq(null), eq(null), anyList(), eq(9L), eq(true)))
                .thenReturn(page);
        when(tags.getTagsByProblemIds(List.of(42L))).thenReturn(List.of(
                new ProblemTagProjection(42L, 7L, "graphs", "#123456")));
        ProblemQueryDTO query = new ProblemQueryDTO();
        query.setCurrent(1);
        query.setSize(20);
        query.setTagIds(List.of());

        IPage<ProblemListItemVO> result = service.getProblemList(query, 9L);

        assertEquals(List.of(7L), result.getRecords().get(0).getTags().stream()
                .map(ProblemTagVO::getId)
                .toList());
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
        assertEquals(true, mapper.contains("JSON_TABLE"));
        assertEquals(true, mapper.contains("$.tags[*]"));
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
        problem.setIsSpecialJudge(false);
        problem.setJudgeMode(0);
        problem.setTotalScore(100);
        problem.setStatus(status);
        problem.setPublishedVersionId(publishedVersionId);
        return problem;
    }

    private ProblemCreateDTO createRequest() {
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
        return request;
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
        version.setProjectionComplete(true);
        version.setStatementJson("""
                {"title":"%s","description":"Published statement","inputDescription":"Published input",
                 "outputDescription":"Published output","hints":[],"samples":[],
                 "source":"published-source","tags":[]}
                """.formatted(title));
        version.setLimitsJson("""
                {"timeLimit":%d,"memoryLimit":%d,"totalScore":%d}
                """.formatted(timeLimit, memoryLimit, totalScore));
        version.setJudgeConfigJson("""
                {"specialJudge":false,"specialJudgeCode":null,"specialJudgeLanguage":null,
                 "judgeMode":%d,"checker":"exact","difficulty":%d}
                """.formatted(judgeMode, difficulty));
        return version;
    }
}
