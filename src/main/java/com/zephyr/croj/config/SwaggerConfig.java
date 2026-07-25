package com.zephyr.croj.config;

import com.zephyr.croj.config.properties.JwtProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;

/**
 * Swagger配置类
 */
@Configuration
@RequiredArgsConstructor
public class SwaggerConfig {

    private final JwtProperties jwtProperties;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeRush OJ API")
                        .version("v1.0.0")
                        .description("CodeRush在线评测系统API文档")
                        .contact(new Contact()
                                .name("HeZephyr")
                                .email("unique.hzf@gmail.com")
                                .url("https://github.com/HeZephyr")))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createSecurityScheme()));
    }

    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme()
                .name(jwtProperties.header())
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .description("请输入JWT Token，格式为: Bearer {token}");
    }
}
