package com.zephyr.croj.problem.importer;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
    void parsesThePinnedFreeProblemSet14FixtureWithoutResolvingItsPublicDoctype() throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream(
                "/problem-import/freeproblemset/fps-zhblue-A+B.xml")) {
            assertThat(fixture).isNotNull();

            ProblemImportBatch batch = parser.parse(fixture);

            assertThat(batch.formatVersion()).isEqualTo("1.4");
            assertThat(batch.sourceUrl()).isEqualTo("https://github.com/zhblue/freeproblemset/");
            assertThat(batch.problems()).hasSize(1);
            ProblemImportDraft problem = batch.problems().get(0);
            assertThat(problem.title()).isEqualTo("A+B Problem");
            assertThat(problem.timeLimitMillis()).isEqualTo(1000);
            assertThat(problem.memoryLimitKilobytes()).isEqualTo(256 * 1024);
            assertThat(problem.tests()).hasSize(3);
            assertThat(problem.tests().get(0).name()).isEqualTo("test_01");
        }
    }

    @Test
    void pairsGroupedInputsAndOutputsByNameAndStablePosition() {
        String xml = """
                <fps version="1.4"><item>
                  <title>Grouped tests</title>
                  <time_limit unit="ms">1000</time_limit>
                  <memory_limit unit="KB">1024</memory_limit>
                  <description>Valid FPS ordering</description>
                  <sample_input>sample one</sample_input>
                  <sample_input>sample two</sample_input>
                  <sample_output>answer one</sample_output>
                  <sample_output>answer two</sample_output>
                  <test_input name="beta">input b</test_input>
                  <test_input name="alpha">input a</test_input>
                  <test_input>input unnamed</test_input>
                  <test_output name="alpha">output a</test_output>
                  <test_output name="beta">output b</test_output>
                  <test_output>output unnamed</test_output>
                </item></fps>
                """;

        ProblemImportDraft problem = parser.parse(stream(xml)).problems().get(0);

        assertThat(problem.samples()).containsExactly(
                new ProblemImportCase(null, "sample one", "answer one"),
                new ProblemImportCase(null, "sample two", "answer two"));
        assertThat(problem.tests()).containsExactly(
                new ProblemImportCase("beta", "input b", "output b"),
                new ProblemImportCase("alpha", "input a", "output a"),
                new ProblemImportCase(null, "input unnamed", "output unnamed"));
    }

    @Test
    void fallsBackToStablePositionWhenOnlyOneSideHasATestName() {
        String xml = """
                <fps version="1.4"><item>
                  <title>Partial names</title>
                  <time_limit unit="ms">1000</time_limit>
                  <memory_limit unit="KB">1024</memory_limit>
                  <description>Optional FPS test names</description>
                  <test_input name="input-name-only">first input</test_input>
                  <test_input>second input</test_input>
                  <test_output>first output</test_output>
                  <test_output name="output-name-only">second output</test_output>
                </item></fps>
                """;

        ProblemImportDraft problem = parser.parse(stream(xml)).problems().get(0);

        assertThat(problem.tests()).containsExactly(
                new ProblemImportCase("input-name-only", "first input", "first output"),
                new ProblemImportCase("output-name-only", "second input", "second output"));
    }

    @Test
    void appliesTheImageLimitInsteadOfTheNormalTextFieldLimitToBase64() {
        byte[] image = "18-byte-image-data".getBytes(StandardCharsets.UTF_8);
        ProblemImportLimits limits = new ProblemImportLimits(
                10_000, 10_000, 10, 20, 10, 10, 10, 10, 64, 10);
        FpsProblemPackageParser limitedParser = new FpsProblemPackageParser(limits);
        String xml = """
                <fps version="1.4"><item>
                  <title>Image</title>
                  <time_limit>1</time_limit>
                  <memory_limit>64</memory_limit>
                  <description>Image fixture</description>
                  <img><src>asset.png</src><base64>%s</base64></img>
                </item></fps>
                """.formatted(Base64.getEncoder().encodeToString(image));

        ProblemImportDraft problem = limitedParser.parse(stream(xml)).problems().get(0);

        assertThat(problem.images()).hasSize(1);
        assertThat(problem.images().get(0).content()).containsExactly(image);
    }

    @Test
    void rejectsPackagesThatExceedTheByteBudget() {
        ProblemImportLimits limits = new ProblemImportLimits(
                128, 10_000, 10, 1_000, 10, 10, 10, 10, 64, 10);
        FpsProblemPackageParser limitedParser = new FpsProblemPackageParser(limits);
        String xml = "<fps version=\"1.4\"><item><title>Oversized</title>"
                + "<time_limit>1</time_limit><memory_limit>64</memory_limit>"
                + "<description>" + "x".repeat(256) + "</description></item></fps>";

        assertThatThrownBy(() -> limitedParser.parse(stream(xml)))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("byte limit");
    }

    @Test
    void rejectsWarningFloodsAtTheConfiguredLimit() {
        ProblemImportLimits limits = new ProblemImportLimits(
                10_000, 10_000, 10, 1_000, 10, 10, 10, 10, 64, 1);
        FpsProblemPackageParser limitedParser = new FpsProblemPackageParser(limits);
        String xml = """
                <fps version="1.4">
                  <unknown-one />
                  <unknown-two />
                </fps>
                """;

        assertThatThrownBy(() -> limitedParser.parse(stream(xml)))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("warning limit");
    }

    @Test
    void configuresTheXmlFactoryToNeverResolveTheCanonicalPublicDoctype() {
        XMLInputFactory factory = FpsProblemPackageParser.secureFactory();

        assertThat(factory.getProperty(XMLInputFactory.SUPPORT_DTD)).isEqualTo(false);
        assertThat(factory.getProperty("javax.xml.stream.isSupportingExternalEntities")).isEqualTo(false);
        assertThat(factory.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES)).isEqualTo(false);
        assertThatThrownBy(() -> factory.getXMLResolver().resolveEntity(
                "public-id", "https://hustoj.com/fps.current.dtd", null, null))
                .isInstanceOf(XMLStreamException.class)
                .hasMessageContaining("prohibited");
    }

    @Test
    void rejectsXmlEventAndProblemCountFloods() {
        FpsProblemPackageParser eventLimited = new FpsProblemPackageParser(
                limits(5, 10, 1_000, 10, 10, 10, 10, 64, 10));
        assertThatThrownBy(() -> eventLimited.parse(stream(validPackage(""))))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("XML event limit");

        FpsProblemPackageParser problemLimited = new FpsProblemPackageParser(
                limits(10_000, 1, 1_000, 10, 10, 10, 10, 64, 10));
        String twoProblems = "<fps version=\"1.4\">" + validItem("") + validItem("") + "</fps>";
        assertThatThrownBy(() -> problemLimited.parse(stream(twoProblems)))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("problem count limit");
    }

    @Test
    void rejectsTextSampleAndHiddenTestFloods() {
        FpsProblemPackageParser textLimited = new FpsProblemPackageParser(
                limits(10_000, 10, 8, 10, 10, 10, 10, 64, 10));
        assertThatThrownBy(() -> textLimited.parse(stream(validPackage(""))))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("title")
                .hasMessageContaining("text limit");

        FpsProblemPackageParser sampleLimited = new FpsProblemPackageParser(
                limits(10_000, 10, 1_000, 1, 10, 10, 10, 64, 10));
        assertThatThrownBy(() -> sampleLimited.parse(stream(validPackage("""
                <sample_input>one</sample_input><sample_input>two</sample_input>
                """))))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("sample_input")
                .hasMessageContaining("count limit");

        FpsProblemPackageParser testLimited = new FpsProblemPackageParser(
                limits(10_000, 10, 1_000, 10, 1, 10, 10, 64, 10));
        assertThatThrownBy(() -> testLimited.parse(stream(validPackage("""
                <test_input>one</test_input><test_input>two</test_input>
                """))))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("hidden test count limit");
    }

    @Test
    void rejectsCodeResourceAndImageCountFloods() {
        FpsProblemPackageParser codeLimited = new FpsProblemPackageParser(
                limits(10_000, 10, 1_000, 10, 10, 1, 10, 64, 10));
        assertThatThrownBy(() -> codeLimited.parse(stream(validPackage("""
                <solution language="C++">one</solution><solution language="Java">two</solution>
                """))))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("code resource count limit");

        String image = Base64.getEncoder().encodeToString(new byte[]{1});
        FpsProblemPackageParser imageCountLimited = new FpsProblemPackageParser(
                limits(10_000, 10, 1_000, 10, 10, 10, 1, 64, 10));
        assertThatThrownBy(() -> imageCountLimited.parse(stream(validPackage("""
                <img><src>one.png</src><base64>%s</base64></img>
                <img><src>two.png</src><base64>%s</base64></img>
                """.formatted(image, image)))))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("image count limit");
    }

    @Test
    void rejectsDecodedImagesOverTheConfiguredByteLimit() {
        String image = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        FpsProblemPackageParser imageLimited = new FpsProblemPackageParser(
                limits(10_000, 10, 1_000, 10, 10, 10, 10, 2, 10));

        assertThatThrownBy(() -> imageLimited.parse(stream(validPackage("""
                <img><src>large.png</src><base64>%s</base64></img>
                """.formatted(image)))))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("image exceeds")
                .hasMessageContaining("size limit");
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
    void rejectsUnresolvedEntitiesAfterTheCanonicalPublicDoctype() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE fps PUBLIC
                  "-//freeproblemset//An opensource XML standard for AlgorithmContest Problem Set//EN"
                  "http://hustoj.com/fps.current.dtd">
                <fps version="1.4"><item>
                  <title>&secret;</title>
                  <time_limit>1</time_limit>
                  <memory_limit>64</memory_limit>
                  <description>Entity fixture</description>
                </item></fps>
                """;

        assertThatThrownBy(() -> parser.parse(stream(xml)))
                .isInstanceOf(ProblemPackageParseException.class)
                .hasMessageContaining("entity reference")
                .hasMessageContaining("prohibited");
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

    private static ProblemImportLimits limits(
            long maxXmlEvents,
            int maxProblems,
            int maxTextCharacters,
            int maxSamples,
            int maxTests,
            int maxCodeResources,
            int maxImages,
            int maxImageBytes,
            int maxWarnings
    ) {
        return new ProblemImportLimits(
                100_000,
                maxXmlEvents,
                maxProblems,
                maxTextCharacters,
                maxSamples,
                maxTests,
                maxCodeResources,
                maxImages,
                maxImageBytes,
                maxWarnings);
    }

    private static String validPackage(String extra) {
        return "<fps version=\"1.4\">" + validItem(extra) + "</fps>";
    }

    private static String validItem(String extra) {
        return """
                <item>
                  <title>Valid item</title>
                  <time_limit>1</time_limit>
                  <memory_limit>64</memory_limit>
                  <description>Valid fixture</description>
                  %s
                </item>
                """.formatted(extra);
    }
}
