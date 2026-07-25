package com.zephyr.croj.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminBootstrapCommandTest {

    @Test
    void successUsesEnvironmentAndNeverPrintsCredentials() {
        Map<String, String> environment = validEnvironment();
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = AdminBootstrapCommand.run(
                environment::get,
                request -> {
                    calls.incrementAndGet();
                    assertEquals("admin", request.username());
                    assertEquals("admin@coderushoj.local", request.email());
                    assertEquals("never-print-this-password", request.password());
                    return AdminBootstrapResult.CREATED;
                },
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertEquals(0, code);
        assertEquals(1, calls.get());
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("created"));
        assertFalse(combined(stdout, stderr).contains("never-print-this-password"));
        assertFalse(combined(stdout, stderr).contains("admin@coderushoj.local"));
    }

    @Test
    void missingConfigurationFailsBeforeDatabaseAccessAndRedactsValues() {
        Map<String, String> environment = validEnvironment();
        environment.remove("BOOTSTRAP_ADMIN_PASSWORD");
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = AdminBootstrapCommand.run(
                environment::get,
                request -> {
                    calls.incrementAndGet();
                    return AdminBootstrapResult.CREATED;
                },
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertEquals(2, code);
        assertEquals(0, calls.get());
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("configuration is incomplete"));
        assertFalse(combined(stdout, stderr).contains(environment.get("DATABASE_PASSWORD")));
    }

    @Test
    void conflictsReturnFailureWithoutLeakingTheRequest() {
        Map<String, String> environment = validEnvironment();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = AdminBootstrapCommand.run(
                environment::get,
                request -> {
                    throw new AdminBootstrapConflictException();
                },
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertEquals(1, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("conflicts with an existing account"));
        assertFalse(combined(stdout, stderr).contains(environment.get("BOOTSTRAP_ADMIN_PASSWORD")));
        assertFalse(combined(stdout, stderr).contains(environment.get("DATABASE_PASSWORD")));
    }

    @Test
    void rejectsCredentialsEmbeddedInTheJdbcUrlBeforeFlywayCanLogThem() {
        Map<String, String> environment = validEnvironment();
        environment.put(
                "DATABASE_URL",
                "jdbc:mysql://mysql:3306/code_rush_oj?user=admin&password=url-secret-value");
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = AdminBootstrapCommand.run(
                environment::get,
                request -> {
                    calls.incrementAndGet();
                    return AdminBootstrapResult.CREATED;
                },
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertEquals(2, code);
        assertEquals(0, calls.get());
        assertFalse(combined(stdout, stderr).contains("url-secret-value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:mysql://address=(host=mysql)(port=3306)(user=admin)(password=address-secret)/code_rush_oj",
        "jdbc:mysql://(host=mysql,port=3306,user=admin,password=descriptor-secret)/code_rush_oj",
        "jdbc:mysql://admin:userinfo-secret@mysql:3306/code_rush_oj",
        "jdbc:mysql://mysql:3306/code_rush_oj?sessionVariables=password=property-secret",
        "jdbc:mysql://mysql:3306/code_rush_oj?unknownProperty=unsafe"
    })
    void rejectsEveryNonAllowlistedOrDescriptorJdbcUrlBeforeDatabaseAccess(String databaseURL) {
        Map<String, String> environment = validEnvironment();
        environment.put("DATABASE_URL", databaseURL);
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int code = AdminBootstrapCommand.run(
                environment::get,
                request -> {
                    calls.incrementAndGet();
                    return AdminBootstrapResult.CREATED;
                },
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertEquals(2, code);
        assertEquals(0, calls.get());
        assertFalse(combined(stdout, stderr).contains("address-secret"));
        assertFalse(combined(stdout, stderr).contains("descriptor-secret"));
        assertFalse(combined(stdout, stderr).contains("userinfo-secret"));
        assertFalse(combined(stdout, stderr).contains("property-secret"));
    }

    @Test
    void acceptsTheDocumentedNonSensitiveJdbcUrlProperties() {
        Map<String, String> environment = validEnvironment();
        environment.put(
                "DATABASE_URL",
                "jdbc:mysql://mysql:3306/code_rush_oj"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
                        + "&forceConnectionTimeZoneToSession=true&useSSL=false");
        AtomicInteger calls = new AtomicInteger();

        int code = AdminBootstrapCommand.run(
                environment::get,
                request -> {
                    calls.incrementAndGet();
                    return AdminBootstrapResult.CREATED;
                },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertEquals(0, code);
        assertEquals(1, calls.get());
    }

    @Test
    void onlyTheExplicitModeActivatesBootstrap() {
        assertTrue(AdminBootstrapCommand.isRequested("bootstrap-admin"));
        assertFalse(AdminBootstrapCommand.isRequested(""));
        assertFalse(AdminBootstrapCommand.isRequested("BOOTSTRAP-ADMIN"));
        assertFalse(AdminBootstrapCommand.isRequested(null));
    }

    private static Map<String, String> validEnvironment() {
        Map<String, String> values = new HashMap<>();
        values.put("DATABASE_URL", "jdbc:mysql://mysql:3306/code_rush_oj");
        values.put("DATABASE_USERNAME", "coderushoj");
        values.put("DATABASE_PASSWORD", "database-secret-value");
        values.put("BOOTSTRAP_ADMIN_USERNAME", "admin");
        values.put("BOOTSTRAP_ADMIN_EMAIL", "admin@coderushoj.local");
        values.put("BOOTSTRAP_ADMIN_PASSWORD", "never-print-this-password");
        return values;
    }

    private static String combined(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        return stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
    }
}
