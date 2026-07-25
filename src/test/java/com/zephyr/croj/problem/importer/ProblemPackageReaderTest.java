package com.zephyr.croj.problem.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ProblemPackageReaderTest {
    private final ProblemImportLimits limits = ProblemImportLimits.defaults();
    private final ProblemPackageReader reader = new ProblemPackageReader(
            new ProblemPackageParserRegistry(List.of(new FpsProblemPackageParser(limits))),
            limits);

    @Test
    void detectsRawFpsXmlAndASafeSingleXmlZip() throws Exception {
        byte[] xml = fps().getBytes(StandardCharsets.UTF_8);

        assertThat(reader.read("problems.xml", xml).format()).isEqualTo(ProblemPackageFormat.FPS_XML);
        assertThat(reader.read("problems.zip", zip("fps/problems.xml", xml)).problems()).hasSize(1);
    }

    @Test
    void rejectsTraversalAndAmbiguousZipPackages() throws Exception {
        byte[] xml = fps().getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read("unsafe.zip", zip("../problems.xml", xml)))
                .isInstanceOf(ProblemPackageParseException.class);
        assertThatThrownBy(() -> reader.read("ambiguous.zip", zip(
                "one.xml", xml,
                "two.xml", xml)))
                .isInstanceOf(ProblemPackageParseException.class);
    }

    private byte[] zip(Object... entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int index = 0; index < entries.length; index += 2) {
                zip.putNextEntry(new ZipEntry((String) entries[index]));
                zip.write((byte[]) entries[index + 1]);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private String fps() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <fps version="1.4">
                  <item>
                    <title>A+B</title><description>Add.</description>
                    <input>Two integers.</input><output>The sum.</output>
                    <time_limit unit="ms">1000</time_limit>
                    <memory_limit unit="mb">64</memory_limit>
                    <sample_input>1 2</sample_input><sample_output>3</sample_output>
                    <test_input name="1">1 2</test_input><test_output name="1">3</test_output>
                  </item>
                </fps>
                """;
    }
}
