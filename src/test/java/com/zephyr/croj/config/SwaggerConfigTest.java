package com.zephyr.croj.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zephyr.croj.config.properties.JwtProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

    @Test
    void publishesOnlyTheOrganizationContactWithoutPersonalEmail() {
        JwtProperties jwtProperties = new JwtProperties(
                "test-only-secret-with-at-least-32-bytes",
                Duration.ofHours(1),
                "Authorization",
                "Bearer ");

        OpenAPI openAPI = new SwaggerConfig(jwtProperties).customOpenAPI();
        Contact contact = openAPI.getInfo().getContact();

        assertThat(contact.getName()).isEqualTo("CodeRushOJ");
        assertThat(contact.getUrl()).isEqualTo("https://github.com/CodeRushOJ");
        assertThat(contact.getEmail()).isNull();
    }
}
