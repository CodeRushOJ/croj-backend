package com.zephyr.croj.problem.importer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemPackageParserRegistryTest {

    @Test
    void delegatesToTheExplicitlySelectedFormat() {
        ProblemImportBatch expected = new ProblemImportBatch(
                ProblemPackageFormat.FPS_XML, "1.2", null, List.of(), List.of());
        ProblemPackageParser parser = parser(ProblemPackageFormat.FPS_XML, expected);
        ProblemPackageParserRegistry registry = new ProblemPackageParserRegistry(List.of(parser));

        ProblemImportBatch actual = registry.parse(
                ProblemPackageFormat.FPS_XML,
                new ByteArrayInputStream(new byte[0]));

        assertThat(actual).isSameAs(expected);
        assertThat(registry.supportedFormats()).containsExactly(ProblemPackageFormat.FPS_XML);
    }

    @Test
    void rejectsDuplicateAndUnavailableFormats() {
        ProblemPackageParser first = parser(ProblemPackageFormat.FPS_XML, null);
        ProblemPackageParser duplicate = parser(ProblemPackageFormat.FPS_XML, null);

        assertThatThrownBy(() -> new ProblemPackageParserRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FPS_XML");

        ProblemPackageParserRegistry registry = new ProblemPackageParserRegistry(List.of(first));
        assertThatThrownBy(() -> registry.parse(
                ProblemPackageFormat.POLYGON_PACKAGE,
                new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("POLYGON_PACKAGE");
    }

    private static ProblemPackageParser parser(ProblemPackageFormat format, ProblemImportBatch result) {
        return new ProblemPackageParser() {
            @Override
            public ProblemPackageFormat format() {
                return format;
            }

            @Override
            public ProblemImportBatch parse(java.io.InputStream input) {
                return result;
            }
        };
    }
}
