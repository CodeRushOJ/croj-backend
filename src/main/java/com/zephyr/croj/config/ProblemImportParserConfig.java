package com.zephyr.croj.config;

import com.zephyr.croj.problem.importer.FpsProblemPackageParser;
import com.zephyr.croj.problem.importer.ProblemImportLimits;
import com.zephyr.croj.problem.importer.ProblemPackageParser;
import com.zephyr.croj.problem.importer.ProblemPackageParserRegistry;
import com.zephyr.croj.problem.importer.ProblemPackageReader;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProblemImportParserConfig {
    @Bean
    ProblemImportLimits problemImportLimits() {
        return ProblemImportLimits.defaults();
    }

    @Bean
    ProblemPackageParser fpsProblemPackageParser(ProblemImportLimits limits) {
        return new FpsProblemPackageParser(limits);
    }

    @Bean
    ProblemPackageParserRegistry problemPackageParserRegistry(List<ProblemPackageParser> parsers) {
        return new ProblemPackageParserRegistry(parsers);
    }

    @Bean
    ProblemPackageReader problemPackageReader(
            ProblemPackageParserRegistry registry,
            ProblemImportLimits limits) {
        return new ProblemPackageReader(registry, limits);
    }
}
