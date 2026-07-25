package com.zephyr.croj.community;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zephyr.croj.security.JwtTokenProvider;
import com.zephyr.croj.service.CommunityContentService;
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
        "spring.datasource.url=jdbc:h2:mem:community-security;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.data.redis.password=test-only",
        "spring.mail.password=test-only",
        "jwt.secret=test-only-secret-with-at-least-32-bytes",
        "app.upload.base-dir=target/test-uploads",
        "app.outbox.enabled=false",
})
@AutoConfigureMockMvc
class CommunityApiSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @MockitoBean private CommunityContentService content;
    @MockitoBean private RocketMQTemplate rocketMq;

    @Test
    void publishedForumAndSolutionReadsArePublic() throws Exception {
        when(content.listPosts(null, 1, 20)).thenReturn(new Page<>());
        when(content.listSolutions(11L, 1, 20)).thenReturn(new Page<>());

        mvc.perform(get("/v1/forum/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/v1/problems/11/solutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void publishingRequiresAValidJwtAndUsesItsUserId() throws Exception {
        String body = """
                {"categoryId":1,"title":"A useful post","contentMarkdown":"Details"}
                """;
        mvc.perform(post("/v1/forum/posts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        when(content.createPost(any(), eq(7L))).thenReturn(41L);
        String token = tokens.createToken(7L, "ada", List.of("USER"));
        mvc.perform(post("/v1/forum/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(41));
    }
}
