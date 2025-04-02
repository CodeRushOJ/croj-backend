package com.zephyr.croj.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC configuration for handling static resources
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.base-dir}")
    private String uploadBaseDir;

    @Value("${app.upload.avatar.url-path}")
    private String avatarUrlPath;

    /**
     * Configure resource handlers to serve static files from external locations
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve the upload directory path to its absolute form
        Path uploadDir = Paths.get(uploadBaseDir)
                .toAbsolutePath().normalize();

        // Register resource handler for uploads directory
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir.toString() + "/")
                .setCachePeriod(3600) // Cache for one hour
                .resourceChain(true);

        // Add specific mapping for avatar URL path if it differs from the general pattern
        if (!avatarUrlPath.equals("/uploads/avatar")) {
            registry.addResourceHandler(avatarUrlPath + "/**")
                    .addResourceLocations("file:" + uploadDir.resolve("avatar").toString() + "/")
                    .setCachePeriod(3600)
                    .resourceChain(true);
        }
    }
}