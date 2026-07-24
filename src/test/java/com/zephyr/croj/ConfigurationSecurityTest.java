package com.zephyr.croj;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigurationSecurityTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void runtimeSecretsMustComeFromTheEnvironment() throws IOException {
        String application = Files.readString(RESOURCES.resolve("application.yml"));

        for (String variable : new String[] {
            "DATABASE_PASSWORD",
            "JWT_SECRET",
            "REDIS_PASSWORD",
            "JUDGE_RESULT_SERVICE_TOKEN",
        }) {
            assertTrue(application.contains("${" + variable + "}"), variable + " must be required");
            assertFalse(application.contains("${" + variable + ":"), variable + " must not have a default");
        }
    }

    @Test
    void optionalSmtpCredentialsMustRemainEnvironmentDriven() throws IOException {
        String application = Files.readString(RESOURCES.resolve("application.yml"));

        assertTrue(application.contains("${SMTP_USERNAME:}"));
        assertTrue(application.contains("${SMTP_PASSWORD:}"));
    }

    @Test
    void profileFilesMustNotContainCommittedCredentials() throws IOException {
        for (String name : new String[] {"application-dev.yml", "application-prod.yml"}) {
            String profile = Files.readString(RESOURCES.resolve(name));
            assertFalse(profile.matches("(?s).*password:\\s*[^$\\s].*"), name + " contains a password");
            assertFalse(profile.matches("(?s).*username:\\s*[^$\\s].*"), name + " contains a username");
        }
    }

    @Test
    void activeProfileMustBeSelectedOutsideTheArtifact() throws IOException {
        String application = Files.readString(RESOURCES.resolve("application.yml"));
        assertFalse(application.matches("(?s).*profiles:\\s*\\n\\s+active:\\s*[^$\\s].*"));
    }

    @Test
    void localEnvironmentFilesMustStayUntracked() throws IOException {
        String gitignore = Files.readString(Path.of(".gitignore"));
        assertTrue(gitignore.lines().anyMatch(".env"::equals));
        assertTrue(gitignore.lines().anyMatch("!.env.example"::equals));
    }

    @Test
    void productionConfigurationMustNotPrintSqlParametersOrRowsToStdout() throws IOException {
        String application = Files.readString(RESOURCES.resolve("application.yml"));
        String development = Files.readString(RESOURCES.resolve("application-dev.yml"));

        assertFalse(application.contains("org.apache.ibatis.logging.stdout.StdOutImpl"));
        assertTrue(development.contains("org.apache.ibatis.logging.stdout.StdOutImpl"));
    }
}
