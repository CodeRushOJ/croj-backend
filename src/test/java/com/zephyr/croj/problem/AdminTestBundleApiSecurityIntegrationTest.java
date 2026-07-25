package com.zephyr.croj.problem;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zephyr.croj.security.JwtTokenProvider;
import com.zephyr.croj.model.vo.AdminProblemVersionSourceVO;
import java.util.List;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-test-bundle-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
        "app.test-bundle.enabled=true",
        "aws.accessKeyId=test-only",
        "aws.secretAccessKey=test-only"
})
@AutoConfigureMockMvc
class AdminTestBundleApiSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @MockitoBean private AdminTestBundleService service;
    @MockitoBean private AdminProblemVersionSourceService sources;
    @MockitoBean private S3Client s3;
    @MockitoBean private RocketMQTemplate rocketMq;

    @Test
    void metadataRequiresAnAdministratorRole() throws Exception {
        String path = "/v1/admin/problems/42/versions/101/test-bundle";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());

        String user = tokens.createToken(7L, "user", List.of("USER"));
        mvc.perform(get(path).header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());

        when(service.describe(42, 101)).thenReturn(new AdminTestBundleService.View(
                42, 101, 1, "DRAFT", false, null, null, null,
                "\"tb-v1-101-DRAFT-none\""));
        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(get(path).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void versionDiscoveryRequiresAnAdministratorAndReturnsDraftIdentifiers() throws Exception {
        String path = "/v1/admin/problems/42/versions";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());

        String user = tokens.createToken(7L, "user", List.of("USER"));
        mvc.perform(get(path).header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());

        AdminTestBundleService.View draft = new AdminTestBundleService.View(
                42L, 101L, 1, "DRAFT", false, null, null, null,
                "\"tb-v1-101-DRAFT-none\"");
        List<AdminTestBundleService.View> versions = List.of(draft);
        when(service.list(42L)).thenReturn(versions);
        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(get(path).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].problemId").value(42))
                .andExpect(jsonPath("$.data[0].versionId").value(101))
                .andExpect(jsonPath("$.data[0].state").value("DRAFT"))
                .andExpect(jsonPath("$.data[0].etag").value("\"tb-v1-101-DRAFT-none\""));
    }

    @Test
    void immutableCheckerSourceRequiresAnAdministratorRole() throws Exception {
        String path = "/v1/admin/problems/42/versions/101/source";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());

        String user = tokens.createToken(7L, "user", List.of("USER"));
        mvc.perform(get(path).header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());

        when(sources.read(42L, 101L)).thenReturn(new AdminProblemVersionSourceVO(
                42L, 101L, 3, "DRAFT", true, "trusted checker", "cpp", 0));
        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(get(path).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.problemId").value(42))
                .andExpect(jsonPath("$.data.versionId").value(101))
                .andExpect(jsonPath("$.data.checkerSource").value("trusted checker"));
    }
}
