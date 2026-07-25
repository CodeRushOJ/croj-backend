package com.zephyr.croj.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zephyr.croj.problem.importer.ProblemPackageFormat;
import com.zephyr.croj.problem.importer.ProblemPackageParserRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ProblemImportParserConfigTest {
    @Test
    void registersTheFpsParserAndRegistryAsSpringBeans() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ProblemImportParserConfig.class)) {
            assertThat(context.getBean(ProblemPackageParserRegistry.class).supportedFormats())
                    .containsExactly(ProblemPackageFormat.FPS_XML);
        }
    }
}
