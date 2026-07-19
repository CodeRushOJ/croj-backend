package com.zephyr.croj.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zephyr.croj.problem.importer.ProblemImportResponses;
import com.zephyr.croj.problem.importer.ProblemImportService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminProblemImportControllerTest {
    private final ProblemImportService imports = mock(ProblemImportService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(request.getAttribute("userId")).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(new AdminProblemImportController(imports, request)).build();
    }

    @Test
    void acceptsRealMultipartPreflightAndReturnsTheFrontendContract() throws Exception {
        byte[] bytes = "<fps/>".getBytes(StandardCharsets.UTF_8);
        when(imports.preflight(7L, "fps.xml", bytes)).thenReturn(new ProblemImportResponses.Preflight(
                "job-42", "FPS_XML", "a".repeat(64), 1, 2,
                List.of(), List.of(), List.of()));

        mvc.perform(multipart("/v1/admin/problem-imports/preflight")
                        .file(new MockMultipartFile("file", "fps.xml", "application/xml", bytes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-42"))
                .andExpect(jsonPath("$.data.detectedFormat").value("FPS_XML"))
                .andExpect(jsonPath("$.data.problemCount").value(1));

        verify(imports).preflight(7L, "fps.xml", bytes);
    }

    @Test
    void commitsAValidatedJobIdempotently() throws Exception {
        when(imports.commit(7L, "job-42"))
                .thenReturn(new ProblemImportResponses.Commit("job-42", "COMMITTED", 2));

        mvc.perform(post("/v1/admin/problem-imports/job-42/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.importedCount").value(2));

        verify(imports).commit(eq(7L), eq("job-42"));
    }
}
