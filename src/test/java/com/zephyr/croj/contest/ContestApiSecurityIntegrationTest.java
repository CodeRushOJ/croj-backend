package com.zephyr.croj.contest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zephyr.croj.security.JwtTokenProvider;
import java.time.Instant;
import java.util.List;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contest-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "spring.data.redis.password=test-only",
        "spring.mail.password=test-only",
        "jwt.secret=test-only-secret-with-at-least-32-bytes",
        "app.judge-result.service-token=judge-result-test-token-with-32-bytes",
        "app.upload.base-dir=target/test-uploads",
        "app.outbox.enabled=false",
})
@AutoConfigureMockMvc
class ContestApiSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @MockitoBean private ContestService contests;
    @MockitoBean private ContestAdminService administrators;
    @MockitoBean private ContestScoreboardService scoreboards;
    @MockitoBean private RocketMQTemplate rocketMq;

    @Test
    void publishedPublicContestListIsAnonymous() throws Exception {
        when(contests.listPublic(1, 20)).thenReturn(new ContestService.ContestPage(1, 20, 0, List.of()));

        mvc.perform(get("/v1/contests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void oiScoreboardContractIsAnonymousAndExplicitlyTyped() throws Exception {
        Instant achievedAt = Instant.parse("2026-07-10T10:40:00Z");
        var problem = new ContestScoreboardService.ProblemScore(
                52L, "A", null, null, null, null, null, 100, 90, 5L, achievedAt);
        var row = new ContestScoreboardService.ScoreboardRow(
                1, 18L, "erin", null, null, null, 90, 1, achievedAt, List.of(problem));
        when(scoreboards.publicScoreboard(2L, null))
                .thenReturn(new ContestScoreboardService.ScoreboardView(
                        2L,
                        "OI",
                        Instant.parse("2026-07-10T11:00:00Z"),
                        true,
                        "sha256",
                        100,
                        List.of(row)));

        mvc.perform(get("/v1/contests/2/scoreboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleType").value("OI"))
                .andExpect(jsonPath("$.data.maximumScore").value(100))
                .andExpect(jsonPath("$.data.rows[0].username").value("erin"))
                .andExpect(jsonPath("$.data.rows[0].totalScore").value(90))
                .andExpect(jsonPath("$.data.rows[0].solved").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].problems[0].submissionId").value(5));
    }

    @Test
    void generatedOpenApiDocumentsScoringSortFreezeAndFieldFamilies() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                                "$.paths['/v1/contests/{contestId}/scoreboard'].get.description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("ACM rows sort"),
                                org.hamcrest.Matchers.containsString("OI rows sort"),
                                org.hamcrest.Matchers.containsString("[start, freeze)"),
                                org.hamcrest.Matchers.containsString("mutually exclusive"))))
                .andExpect(jsonPath(
                                "$.paths['/v1/admin/contests/{contestId}/scoreboard'].get.description")
                        .value(org.hamcrest.Matchers.containsString(
                                "without applying the public freeze")));
    }

    @Test
    void registrationRequiresLogin() throws Exception {
        mvc.perform(post("/v1/contests/1/registrations"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(contests);
    }

    @Test
    void personalContestStatusRequiresLogin() throws Exception {
        mvc.perform(get("/v1/contests/1/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(contests);
    }

    @Test
    void kubernetesHealthProbesArePublicButOtherActuatorSurfacesStayProtected() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contestAdministrationRequiresAdminRole() throws Exception {
        String body = """
                {"title":"Weekly 1","descriptionMarkdown":"ACM",
                 "ruleType":"ACM","visibility":"PUBLIC",
                 "registrationOpensAt":"2026-07-01T00:00:00Z",
                 "registrationClosesAt":"2026-07-09T00:00:00Z",
                 "startsAt":"2026-07-10T00:00:00Z",
                 "freezeAt":"2026-07-10T01:30:00Z",
                 "endsAt":"2026-07-10T02:00:00Z"}
                """;
        String user = tokens.createToken(7L, "user", List.of("USER"));
        mvc.perform(post("/v1/admin/contests")
                        .header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        when(administrators.create(any(), eq(9L))).thenReturn(12L);
        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(post("/v1/admin/contests")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(12));
    }
}
