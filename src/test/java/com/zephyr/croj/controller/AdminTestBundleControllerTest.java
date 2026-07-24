package com.zephyr.croj.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zephyr.croj.common.exception.GlobalExceptionHandler;
import com.zephyr.croj.config.properties.TestBundleProperties;
import com.zephyr.croj.problem.AdminTestBundleService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminTestBundleControllerTest {
    private final AdminTestBundleService service = mock(AdminTestBundleService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TestBundleProperties properties = new TestBundleProperties();
        properties.setMaxArchiveBytes(1024);
        mvc = MockMvcBuilders.standaloneSetup(new AdminTestBundleController(service, properties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void readsMetadataAndExposesItsStrongEtag() throws Exception {
        when(service.describe(42, 101)).thenReturn(view("DRAFT", false, "\"tb-v1-101-DRAFT-none\""));

        mvc.perform(get("/v1/admin/problems/42/versions/101/test-bundle"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"tb-v1-101-DRAFT-none\""))
                .andExpect(jsonPath("$.data.problemId").value(42))
                .andExpect(jsonPath("$.data.versionId").value(101))
                .andExpect(jsonPath("$.data.attached").value(false));
    }

    @Test
    void bindsTheMultipartArchiveUsingIfMatchAndReturnsTheNextEtag() throws Exception {
        byte[] archive = "PK\u0003\u0004zip".getBytes(StandardCharsets.ISO_8859_1);
        when(service.upload(42, 101, "\"tb-v1-101-DRAFT-none\"", archive))
                .thenReturn(view("DRAFT", true, "\"tb-v1-101-DRAFT-abc\""));

        mvc.perform(multipart("/v1/admin/problems/42/versions/101/test-bundle")
                        .file(new MockMultipartFile("file", "tests.zip", "application/zip", archive))
                        .header("If-Match", "\"tb-v1-101-DRAFT-none\"")
                        .with(httpMethod("PUT")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"tb-v1-101-DRAFT-abc\""))
                .andExpect(jsonPath("$.data.attached").value(true));

        verify(service).upload(42, 101, "\"tb-v1-101-DRAFT-none\"", archive);
    }

    @Test
    void publishesTheAttachedBundleUsingIfMatch() throws Exception {
        when(service.publish(42, 101, "\"tb-v1-101-DRAFT-abc\""))
                .thenReturn(view("PUBLISHED", true, "\"tb-v1-101-PUBLISHED-abc\""));

        mvc.perform(post("/v1/admin/problems/42/versions/101/test-bundle/publish")
                        .header("If-Match", "\"tb-v1-101-DRAFT-abc\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"tb-v1-101-PUBLISHED-abc\""))
                .andExpect(jsonPath("$.data.state").value("PUBLISHED"));
    }

    @Test
    void requiresIfMatchInsteadOfSilentlyOverwritingConcurrentState() throws Exception {
        byte[] archive = "PK\u0003\u0004zip".getBytes(StandardCharsets.ISO_8859_1);

        mvc.perform(multipart("/v1/admin/problems/42/versions/101/test-bundle")
                        .file(new MockMultipartFile("file", "tests.zip", "application/zip", archive))
                        .with(httpMethod("PUT")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(42800));
    }

    @Test
    void rejectsAnArchiveLargerThanTheConfiguredLimitBeforeCallingTheService() throws Exception {
        byte[] archive = new byte[1025];

        mvc.perform(multipart("/v1/admin/problems/42/versions/101/test-bundle")
                        .file(new MockMultipartFile("file", "tests.zip", "application/zip", archive))
                        .header("If-Match", "\"tb-v1-101-DRAFT-none\"")
                        .with(httpMethod("PUT")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(41300));

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    private AdminTestBundleService.View view(String state, boolean attached, String etag) {
        return new AdminTestBundleService.View(
                42, 101, 1, state, attached, attached ? "abc" : null,
                attached ? 123L : null, attached ? "{\"schemaVersion\":1}" : null, etag);
    }

    private RequestPostProcessor httpMethod(String method) {
        return request -> {
            request.setMethod(method);
            return request;
        };
    }
}
