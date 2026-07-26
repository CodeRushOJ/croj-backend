package com.zephyr.croj.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReleaseVersionContractTest {

    private static final Pattern PROJECT_VERSION = Pattern.compile(
            "<groupId>com\\.zephyr</groupId>\\s*"
                    + "<artifactId>croj</artifactId>\\s*"
                    + "<version>([^<]+)</version>");

    @Test
    void releaseCandidateUsesOneVersionAcrossArtifactDocsAndImagePublication() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        Matcher matcher = PROJECT_VERSION.matcher(pom);
        assertThat(matcher.find()).as("project version in pom.xml").isTrue();
        String projectVersion = matcher.group(1);

        assertThat(projectVersion).isEqualTo("1.0.4");
        assertThat(Files.readString(Path.of("README.md")))
                .contains("当前发布候选版本为 `v" + projectVersion + "`");
        assertThat(Files.readString(Path.of("CHANGELOG.md")))
                .contains("## [" + projectVersion + "] - 2026-07-26");
        assertThat(Files.readString(Path.of("Dockerfile")))
                .contains("ARG VERSION=v" + projectVersion);

        String workflow = Files.readString(Path.of(".github", "workflows", "image.yml"));
        assertThat(workflow)
                .contains("POM_VERSION=\"$(scripts/project-version.sh)\"")
                .contains("printf 'version=v%s\\n' \"$POM_VERSION\"")
                .contains("test \"$GITHUB_REF_NAME\" = \"v$POM_VERSION\"");
    }
}
