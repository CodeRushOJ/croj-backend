package com.zephyr.croj.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.config.properties.TestBundleProperties;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.mapper.TestBundleMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import com.zephyr.croj.model.entity.TestBundle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestBundleV2ContractExportTest {
    private static final String OUTPUT_PROPERTY = "croj.contract.v2.output";

    @Test
    void exportsAValidatedOiSpecialBundleForTheJudgingConsumer() throws Exception {
        String checkerSource = "#include <iostream>\nint main(){return 0;}\n";
        String sourceSha256 = sha256(checkerSource.getBytes(StandardCharsets.UTF_8));
        String manifest = """
                {"schemaVersion":2,"judgeMode":"OI","checker":"special",
                 "limits":{"timeLimitMillis":1000,"memoryLimitMiB":64},"totalScore":100,
                 "specialJudge":{"language":"cpp","source":"checker/main.cpp",
                   "sourceSha256":"%s","timeLimitMillis":2000,"memoryLimitMiB":128},
                 "cases":[
                  {"id":"first","input":"data/1.in","output":"answers/1.out","weight":30},
                  {"id":"second","input":"data/2.in","output":"answers/2.out","weight":70}
                 ]}
                """.formatted(sourceSha256);
        byte[] archive = new TestBundleArchiveWriter().write(manifest, Map.of(
                "checker/main.cpp", checkerSource.getBytes(StandardCharsets.UTF_8),
                "data/1.in", "1".getBytes(StandardCharsets.UTF_8),
                "answers/1.out", "one".getBytes(StandardCharsets.UTF_8),
                "data/2.in", "2".getBytes(StandardCharsets.UTF_8),
                "answers/2.out", "two".getBytes(StandardCharsets.UTF_8)));

        TestBundleMapper bundles = mock(TestBundleMapper.class);
        ProblemVersionMapper versions = mock(ProblemVersionMapper.class);
        TestBundleStorage storage = mock(TestBundleStorage.class);
        when(bundles.insert(any(TestBundle.class))).thenReturn(1);
        when(versions.selectById(101L)).thenReturn(version(checkerSource));
        var attached = new TestBundleService(
                        bundles,
                        versions,
                        storage,
                        new ObjectMapper(),
                        new TestBundleProperties())
                .attach(42L, 101L, archive);

        assertEquals(
                new ObjectMapper().readTree(manifest),
                new ObjectMapper().readTree(attached.getManifestJson()));
        assertEquals(sha256(archive), attached.getSha256());

        String configuredOutput = System.getProperty(OUTPUT_PROPERTY);
        if (configuredOutput != null && !configuredOutput.isBlank()) {
            Path output = Path.of(configuredOutput);
            if (!output.isAbsolute()
                    || output.getParent() == null
                    || !Files.isDirectory(output.getParent(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(
                        OUTPUT_PROPERTY + " must name a new file in an existing absolute directory");
            }
            Files.write(
                    output,
                    archive,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
    }

    private ProblemVersion version(String checkerSource) throws Exception {
        ProblemVersion version = new ProblemVersion();
        version.setId(101L);
        version.setProblemId(42L);
        version.setState("DRAFT");
        version.setProjectionComplete(true);
        version.setStatementJson("""
                {"title":"SPJ","description":"D","inputDescription":"I",
                 "outputDescription":"O","hints":[],"samples":[],"source":null,"tags":[]}
                """);
        version.setLimitsJson("{\"timeLimit\":1000,\"memoryLimit\":64,\"totalScore\":100}");
        version.setJudgeConfigJson("""
                {"specialJudge":true,"specialJudgeCode":%s,"specialJudgeLanguage":"cpp",
                 "judgeMode":1,"checker":"special","difficulty":2}
                """.formatted(new ObjectMapper().writeValueAsString(checkerSource)));
        return version;
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
