package com.zephyr.croj.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zephyr.croj.config.properties.CorsProperties;
import com.zephyr.croj.config.properties.JwtProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimePropertiesTest {

    @Test
    void jwtSecretMustProvideAtLeast256BitsOfInput() {
        assertThrows(IllegalArgumentException.class, () ->
                new JwtProperties("too-short", Duration.ofHours(24), "Authorization", "Bearer"));

        JwtProperties properties = new JwtProperties(
                "test-only-secret-with-at-least-32-bytes",
                Duration.ofHours(24),
                "Authorization",
                "Bearer");
        assertEquals(Duration.ofHours(24), properties.expiration());
    }

    @Test
    void credentialedCorsMustRejectWildcardOrigins() {
        assertThrows(IllegalArgumentException.class, () ->
                new CorsProperties(List.of("*"), true));

        CorsProperties properties = new CorsProperties(List.of("http://localhost:3000"), true);
        assertEquals(List.of("http://localhost:3000"), properties.allowedOrigins());
    }
}
