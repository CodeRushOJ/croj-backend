package com.zephyr.croj;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BuildModernizationTest {

    @Test
    void dependenciesUseTheSupportedJava17Bridge() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<version>3.5.16</version>"));
        assertTrue(pom.contains("<rocketmq.version>2.3.6</rocketmq.version>"));
        assertTrue(pom.contains("mybatis-plus-spring-boot3-starter"));
        assertTrue(pom.contains("springdoc-openapi-starter-webmvc-ui"));
        assertTrue(pom.contains("spring-boot-starter-actuator"));
    }

    @Test
    void productionDependenciesEnforcePublishedSecurityFloors() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertFalse(pom.contains("<artifactId>kaptcha</artifactId>"));
        assertTrue(pom.contains("<netty.version>4.1.136.Final</netty.version>"));
        assertTrue(pom.contains("<grpc.version>1.75.0</grpc.version>"));
        assertTrue(pom.contains("<protobuf.version>3.25.5</protobuf.version>"));
        assertTrue(pom.contains("<commons-beanutils.version>1.11.0</commons-beanutils.version>"));
        assertTrue(pom.contains("<lz4-java.version>1.10.1</lz4-java.version>"));
        assertTrue(pom.contains("<groupId>at.yawk.lz4</groupId>"));
        assertTrue(pom.contains("<artifactId>rocketmq-logback-classic</artifactId>"));
        assertTrue(pom.contains("<artifactId>rocketmq-shaded-slf4j-api-bridge</artifactId>"));
        assertTrue(pom.contains("<artifactId>maven-enforcer-plugin</artifactId>"));
    }

    @Test
    void applicationCodeUsesJakartaApis() throws IOException {
        try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = Files.readString(source);
                assertFalse(code.contains("import javax.annotation."), source.toString());
                assertFalse(code.contains("import javax.mail."), source.toString());
                assertFalse(code.contains("import javax.servlet."), source.toString());
                assertFalse(code.contains("import javax.validation."), source.toString());
            }
        }
    }

    @Test
    void signedVersionTagsPublishImmutableMultiArchitectureImages() throws IOException {
        String workflow = Files.readString(Path.of(".github", "workflows", "image.yml"));

        assertTrue(workflow.contains("packages: write"));
        assertTrue(workflow.contains("docker/login-action@"));
        assertTrue(workflow.contains("github.ref_type == 'tag'"));
        assertTrue(workflow.contains("platforms: linux/amd64,linux/arm64"));
        assertTrue(workflow.contains("push: true"));
        assertTrue(workflow.contains("ghcr.io/coderushoj/croj-backend:${{ github.ref_name }}"));
        assertTrue(workflow.contains("ghcr.io/coderushoj/croj-backend:sha-${{ github.sha }}"));
        assertFalse(workflow.contains("croj-backend:latest"));
    }
}
