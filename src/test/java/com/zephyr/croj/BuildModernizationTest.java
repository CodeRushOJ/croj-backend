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
}
