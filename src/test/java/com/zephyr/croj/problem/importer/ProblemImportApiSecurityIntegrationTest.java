package com.zephyr.croj.problem.importer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zephyr.croj.security.JwtTokenProvider;
import java.util.List;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:problem-import-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
        "aws.secretAccessKey=test-only",
})
@AutoConfigureMockMvc
class ProblemImportApiSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @MockitoBean private ProblemImportService imports;
    @MockitoBean private S3Client s3;
    @MockitoBean private RocketMQTemplate rocketMq;

    @Test
    void preflightRequiresAnAdministratorAndUsesTheJwtActor() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fps.xml", "application/xml", "<fps/>".getBytes());
        mvc.perform(multipart("/v1/admin/problem-imports/preflight").file(file))
                .andExpect(status().isUnauthorized());

        String user = tokens.createToken(7L, "user", List.of("USER"));
        mvc.perform(multipart("/v1/admin/problem-imports/preflight")
                        .file(file)
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());

        when(imports.preflight(anyLong(), anyString(), any())).thenReturn(
                new ProblemImportResponses.Preflight(
                        "job-42", "FPS_XML", "a".repeat(64), 1, 1,
                        List.of(), List.of(), List.of()));
        String admin = tokens.createToken(9L, "admin", List.of("ADMIN"));
        mvc.perform(multipart("/v1/admin/problem-imports/preflight")
                        .file(file)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-42"));
    }
}
