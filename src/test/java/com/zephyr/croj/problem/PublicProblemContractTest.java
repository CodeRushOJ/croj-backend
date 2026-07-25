package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.config.SwaggerConfig;
import com.zephyr.croj.config.properties.JwtProperties;
import com.zephyr.croj.controller.ProblemController;
import com.zephyr.croj.controller.AdminAnnouncementController;
import com.zephyr.croj.controller.AdminContestController;
import com.zephyr.croj.controller.ContestController;
import com.zephyr.croj.controller.ForumController;
import com.zephyr.croj.controller.SolutionController;
import com.zephyr.croj.model.vo.ProblemVO;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class PublicProblemContractTest {

    @Test
    void publicProblemJsonCanNeverSerializeCheckerSource() {
        ProblemVO problem = new ProblemVO();
        problem.setId(42L);

        JsonNode json = new ObjectMapper().valueToTree(problem);

        assertFalse(Arrays.stream(ProblemVO.class.getDeclaredFields())
                .anyMatch(field -> "specialJudgeCode".equals(field.getName())));
        assertFalse(json.has("specialJudgeCode"));
    }

    @Test
    void anonymousProblemOperationsDoNotDeclareBearerAuthentication() throws Exception {
        for (Method method : List.of(
                ProblemController.class.getMethod("getProblemById", Long.class),
                ProblemController.class.getMethod("getProblemByNo", String.class),
                ProblemController.class.getMethod(
                        "getProblemList", com.zephyr.croj.model.dto.ProblemQueryDTO.class))) {
            assertEquals(0, method.getAnnotation(Operation.class).security().length, method.getName());
        }
    }

    @Test
    void openApiDoesNotApplyBearerAuthenticationGlobally() {
        JwtProperties jwt = new JwtProperties(
                "01234567890123456789012345678901",
                Duration.ofHours(1),
                "Authorization",
                "Bearer ");

        assertNull(new SwaggerConfig(jwt).customOpenAPI().getSecurity());
    }

    @Test
    void protectedOperationsExplicitlyDeclareBearerAuthentication() throws Exception {
        for (Class<?> controller : List.of(
                AdminAnnouncementController.class,
                AdminContestController.class)) {
            assertEquals(
                    "Bearer Authentication",
                    controller.getAnnotation(SecurityRequirement.class).name(),
                    controller.getSimpleName());
        }
        for (Method method : List.of(
                ForumController.class.getMethod(
                        "createPost", com.zephyr.croj.model.dto.CreateForumPostDTO.class),
                ForumController.class.getMethod("deletePost", Long.class),
                SolutionController.class.getMethod(
                        "publishSolution",
                        Long.class,
                        com.zephyr.croj.model.dto.PublishSolutionDTO.class),
                SolutionController.class.getMethod("deleteSolution", Long.class, Long.class),
                ContestController.class.getMethod("me", long.class),
                ContestController.class.getMethod("register", long.class),
                ContestController.class.getMethod("cancelRegistration", long.class),
                ContestController.class.getMethod(
                        "ask",
                        long.class,
                        com.zephyr.croj.model.dto.contest.ContestRequests.Clarification.class))) {
            assertEquals(
                    "Bearer Authentication",
                    method.getAnnotation(SecurityRequirement.class).name(),
                    method.toString());
        }
    }

    @Test
    void checkerSourceHasASeparateExplicitlyAuthorizedAdminContract() throws Exception {
        Class<?> controller = assertDoesNotThrow(
                () -> Class.forName(
                        "com.zephyr.croj.controller.AdminProblemVersionSourceController"));
        Class<?> dto = assertDoesNotThrow(
                () -> Class.forName("com.zephyr.croj.model.vo.AdminProblemVersionSourceVO"));

        assertTrue(Arrays.stream(dto.getDeclaredFields())
                .anyMatch(field -> "checkerSource".equals(field.getName())));
        assertTrue(controller.isAnnotationPresent(PreAuthorize.class));
        assertEquals(
                "hasRole('ADMIN') or hasRole('SUPER_ADMIN')",
                controller.getAnnotation(PreAuthorize.class).value());
        assertEquals(
                "/v1/admin/problems/{problemId}/versions/{versionId}/source",
                controller.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals(
                "Bearer Authentication",
                controller.getAnnotation(SecurityRequirement.class).name());
    }

    @Test
    void adminSourceContractReadsCheckerSourceFromTheRequestedImmutableVersion()
            throws Exception {
        Class<?> serviceType = assertDoesNotThrow(
                () -> Class.forName(
                        "com.zephyr.croj.problem.AdminProblemVersionSourceService"));
        ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setVersionNo(3);
        version.setState("DRAFT");
        version.setJudgeConfigJson("""
                {"specialJudge":true,"specialJudgeCode":"trusted checker",
                 "specialJudgeLanguage":"cpp","judgeMode":0,"difficulty":2}
                """);
        when(versions.selectById(101L)).thenReturn(version);
        Object service = serviceType
                .getConstructor(ProblemVersionMapper.class, ObjectMapper.class)
                .newInstance(versions, new ObjectMapper());

        Object view = serviceType
                .getMethod("read", long.class, long.class)
                .invoke(service, 42L, 101L);

        assertEquals(
                "trusted checker",
                view.getClass().getMethod("checkerSource").invoke(view));
    }
}
