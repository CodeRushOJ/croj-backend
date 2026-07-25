package com.zephyr.croj.judge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zephyr.croj.model.dto.JudgeResultRequest;
import com.zephyr.croj.model.vo.JudgeResultResponse;
import com.zephyr.croj.security.JudgeServiceTokenFilter;
import com.zephyr.croj.security.JwtTokenProvider;
import com.zephyr.croj.service.JudgeResultService;
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
        "spring.datasource.url=jdbc:h2:mem:judge-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        "spring.data.redis.password=test-only",
        "spring.mail.password=test-only",
        "jwt.secret=test-only-secret-with-at-least-32-bytes",
        "app.judge-result.service-token=judge-result-test-token-with-32-bytes",
        "app.upload.base-dir=target/test-uploads",
        "app.outbox.enabled=false",
})
@AutoConfigureMockMvc
class JudgeResultApiSecurityIntegrationTest {
    private static final String ENDPOINT = "/internal/v1/judge-results";
    private static final String TOKEN = "judge-result-test-token-with-32-bytes";
    private static final String VALID_BODY = """
            {
              "resultId":"result-1",
              "submissionId":99,
              "attemptNo":1,
              "status":"ACCEPTED",
              "exitCode":0,
              "timeUsedMillis":12,
              "memoryUsedKb":2048,
              "stdout":"ok",
              "stderr":"",
              "compileError":""
            }
            """;

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @MockitoBean private JudgeResultService results;
    @MockitoBean private RocketMQTemplate rocketMq;

    @Test
    void missingOrWrongServiceTokenIsUnauthorized() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ENDPOINT)
                        .header(JudgeServiceTokenFilter.TOKEN_HEADER, "wrong-token-with-enough-length-but-wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        String userToken = tokens.createToken(7L, "ada", List.of("USER"));
        mvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer " + userToken)
                        .header(JudgeServiceTokenFilter.TOKEN_HEADER, "wrong-token-with-enough-length-but-wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(results);
    }

    @Test
    void exactServiceTokenAllowsTheRequest() throws Exception {
        when(results.ingest(any())).thenReturn(new JudgeResultResponse("APPLIED"));

        mvc.perform(post(ENDPOINT)
                        .header(JudgeServiceTokenFilter.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disposition").value("APPLIED"));

        verify(results).ingest(any(JudgeResultRequest.class));
    }

    @Test
    void invalidMetricsAreRejectedBeforeTheServiceLayer() throws Exception {
        String invalid = VALID_BODY.replace("\"timeUsedMillis\":12", "\"timeUsedMillis\":-1");

        mvc.perform(post(ENDPOINT)
                        .header(JudgeServiceTokenFilter.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(results);
    }

    @Test
    void incompleteOIScoreIsRejectedBeforeTheServiceLayer() throws Exception {
        String invalid = VALID_BODY.replace(
                "\"memoryUsedKb\":2048", "\"memoryUsedKb\":2048,\"score\":70");

        mvc.perform(post(ENDPOINT)
                        .header(JudgeServiceTokenFilter.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(results);
    }
}
