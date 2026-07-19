package com.zephyr.croj.announcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    "spring.datasource.url=jdbc:h2:mem:announcement-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class AnnouncementApiSecurityIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-19T02:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @MockitoBean private AnnouncementService announcements;
    @MockitoBean private RocketMQTemplate rocketMq;

    @Test
    void publicAnnouncementFeedAndDetailAreAnonymous() throws Exception {
        when(announcements.listPublic(1, 20))
                .thenReturn(new AnnouncementService.PublicPage(1, 20, 1, List.of(publicAnnouncement())));
        when(announcements.current(5)).thenReturn(List.of(publicAnnouncement()));
        when(announcements.detail(4L)).thenReturn(publicAnnouncement());

        mvc.perform(get("/v1/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("Maintenance"));
        mvc.perform(get("/v1/announcements/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(4));
        mvc.perform(get("/v1/announcements/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void publicPaginationAndCurrentLimitAreValidated() throws Exception {
        mvc.perform(get("/v1/announcements").param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/announcements/current").param("limit", "21"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(announcements);
    }

    @Test
    void announcementAdministrationRequiresAnAdministratorRole() throws Exception {
        mvc.perform(get("/v1/admin/announcements"))
                .andExpect(status().isUnauthorized());

        String user = tokens.createToken(7L, "user", List.of("USER"));
        mvc.perform(get("/v1/admin/announcements")
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());

        when(announcements.listAdmin(1, 20, null))
                .thenReturn(new AnnouncementService.AdminPage(1, 20, 0, null, List.of()));
        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(get("/v1/admin/announcements")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void administratorCanCreateUpdateAndDriveEveryLifecycleAction() throws Exception {
        String admin = tokens.createToken(9L, "admin", List.of("SUPER_ADMIN"));
        String draft = """
                {"title":"Maintenance","contentMarkdown":"Tonight","pinned":true,"pinOrder":2}
                """;
        when(announcements.create(any(), eq(9L))).thenReturn(4L);

        mvc.perform(post("/v1/admin/announcements")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4));
        mvc.perform(put("/v1/admin/announcements/4")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draft))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/admin/announcements/4/schedule")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publishAt\":\"2026-07-20T02:00:00Z\",\"expiresAt\":null}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/admin/announcements/4/publish")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":null}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/admin/announcements/4/withdraw")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\""))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/admin/announcements/4/archive")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\""))
                .andExpect(status().isOk());

        verify(announcements).update(eq(4L), eq(7L), any(), eq(9L));
        verify(announcements).schedule(eq(4L), eq(7L), any(), eq(9L));
        verify(announcements).publish(eq(4L), eq(7L), any(), eq(9L));
        verify(announcements).withdraw(4L, 7L, 9L);
        verify(announcements).archive(4L, 7L, 9L);
    }

    @Test
    void invalidDraftBodyReturnsBadRequestBeforeServiceInvocation() throws Exception {
        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(post("/v1/admin/announcements")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"contentMarkdown\":\"\",\"pinOrder\":10001}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(announcements);
    }

    @Test
    void announcementDomainErrorsPreserveHttpSemantics() throws Exception {
        when(announcements.detail(404L)).thenThrow(AnnouncementApiException.notFound());
        mvc.perform(get("/v1/announcements/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        org.mockito.Mockito.doThrow(AnnouncementApiException.conflict())
                .when(announcements).withdraw(4L, 7L, 9L);
        mvc.perform(post("/v1/admin/announcements/4/withdraw")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));

        org.mockito.Mockito.doThrow(AnnouncementApiException.unprocessable("invalid transition"))
                .when(announcements).archive(5L, 7L, 9L);
        mvc.perform(post("/v1/admin/announcements/5/archive")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42200));
    }

    @Test
    void malformedQueryJsonAndMissingVersionAreBadRequests() throws Exception {
        mvc.perform(get("/v1/announcements").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(post("/v1/admin/announcements/4/schedule")
                        .header("Authorization", "Bearer " + admin)
                        .header("If-Match", "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publishAt\":\"not-an-instant\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mvc.perform(post("/v1/admin/announcements/4/archive")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    private AnnouncementService.PublicAnnouncement publicAnnouncement() {
        return new AnnouncementService.PublicAnnouncement(
                4L,
                "Maintenance",
                "Tonight",
                true,
                2,
                AnnouncementLifecycle.PUBLISHED,
                NOW,
                null,
                NOW);
    }
}
