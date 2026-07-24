package com.zephyr.croj.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.zephyr.croj.config.properties.CorsProperties;
import com.zephyr.croj.security.JwtAccessDeniedHandler;
import com.zephyr.croj.security.JwtAuthenticationEntryPoint;
import com.zephyr.croj.security.JwtAuthenticationFilter;
import com.zephyr.croj.security.JudgeServiceTokenFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigurationTest {

    @Test
    void allowsOptimisticConcurrencyHeadersUsedByAdminApis() {
        SecurityConfig security = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(JwtAuthenticationEntryPoint.class),
                mock(JwtAccessDeniedHandler.class),
                new CorsProperties(List.of("https://oj.example.test"), true),
                mock(JudgeServiceTokenFilter.class));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/v1/admin/announcements/9");
        request.addHeader("Origin", "https://oj.example.test");
        request.addHeader("Access-Control-Request-Method", "PUT");

        CorsConfiguration cors = security.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedHeaders())
                .contains("Authorization", "Content-Type", "If-Match");
        assertThat(cors.getExposedHeaders()).contains("ETag");
    }
}
