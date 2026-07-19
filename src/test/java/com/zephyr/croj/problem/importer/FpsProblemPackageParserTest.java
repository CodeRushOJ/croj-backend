package com.zephyr.croj.problem.importer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FpsProblemPackageParserTest {

    private final FpsProblemPackageParser parser = new FpsProblemPackageParser(ProblemImportLimits.defaults());

    @Test
    void parsesFps12IntoTheCanonicalDraft() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <fps version="1.2" url="https://github.com/zhblue/freeproblemset/">
                  <generator name="fixture" url="https://example.test" />
                  <item>
                    <title><![CDATA[A + B]]></title>
                    <time_limit unit="s">1.5</time_limit>
                    <memory_limit unit="MB">64</memory_limit>
                    <description><![CDATA[Add two integers.]]></description>
                    <input><![CDATA[Two integers.]]></input>
                    <output><![CDATA[Their sum.]]></output>
                    <sample_input><![CDATA[1 2]]></sample_input>
                    <sample_output><![CDATA[3]]></sample_output>
                    <test_input name="01"><![CDATA[2 3]]></test_input>
                    <test_output name="01"><![CDATA[5]]></test_output>
                    <source><![CDATA[CodeRush fixture]]></source>
                    <solution language="C++"><![CDATA[int main(){}]]></solution>
                    <spj language="C++"><![CDATA[int main(){}]]></spj>
                    <future_extension>must not be silent</future_extension>
                  </item>
                </fps>
                """;

        ProblemImportBatch batch = parser.parse(stream(xml));

        assertThat(batch.format()).isEqualTo(ProblemPackageFormat.FPS_XML);
        assertThat(batch.formatVersion()).isEqualTo("1.2");
        assertThat(batch.problems()).hasSize(1);
        ProblemImportDraft problem = batch.problems().get(0);
        assertThat(problem.title()).isEqualTo("A + B");
        assertThat(problem.timeLimitMillis()).isEqualTo(1500);
        assertThat(problem.memoryLimitKilobytes()).isEqualTo(65536);
        assertThat(problem.samples()).containsExactly(new ProblemImportCase(null, "1 2", "3"));
        assertThat(problem.tests()).containsExactly(new ProblemImportCase("01", "2 3", "5"));
        assertThat(problem.codeResources())
                .extracting(ProblemImportCodeResource::kind)
                .containsExactly(ProblemImportCodeKind.SOLUTION, ProblemImportCodeKind.SPECIAL_JUDGE);
        assertThat(batch.sourceUrl()).isEqualTo("https://github.com/zhblue/freeproblemset/");
        assertThat(batch.warnings()).containsExactly("Unsupported FPS item element: future_extension");
    }

    @Test
    void rejectsDoctypeAndExternalEntities() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE fps [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <fps version="1.2"><item><title>&secret;</title></item></fps>
                """;

        assertThatThrownBy(() -> parser.parse(stream(xml)))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("DTD");
    }

    @Test
    void rejectsUnpairedHiddenTests() {
        String xml = """
                <fps version="1.2"><item>
                  <title>Broken</title>
                  <time_limit unit="ms">1000</time_limit>
                  <memory_limit unit="KB">1024</memory_limit>
                  <description>Broken fixture</description>
                  <test_input>1</test_input>
                </item></fps>
                """;

        assertThatThrownBy(() -> parser.parse(stream(xml)))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("test_input")
                .hasMessageContaining("test_output");
    }

    @Test
    void rejectsUnsupportedFpsVersions() {
        String xml = "<fps version=\"9.9\"></fps>";

        assertThatThrownBy(() -> parser.parse(stream(xml)))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("9.9");
    }

    private static ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
