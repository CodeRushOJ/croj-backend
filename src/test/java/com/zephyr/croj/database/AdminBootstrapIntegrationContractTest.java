package com.zephyr.croj.database;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminBootstrapIntegrationContractTest {

    private static final Path MYSQL_GATE = Path.of("tests", "integration", "admin-bootstrap-mysql84.sh");
    private static final Path IMAGE_WORKFLOW = Path.of(".github", "workflows", "image.yml");

    @Test
    void productionImageWorkflowRunsTheMySql84BootstrapGate() throws IOException {
        assertTrue(Files.isRegularFile(MYSQL_GATE));
        String script = Files.readString(MYSQL_GATE);
        assertTrue(script.contains("mysql:8.4.10"));
        assertTrue(script.contains("flyway_schema_history"));
        assertTrue(script.contains("administrator_id"));
        assertTrue(script.contains("BOOTSTRAP_ADMIN_PASSWORD"));
        assertTrue(script.contains("mysql --protocol TCP --host 127.0.0.1"));

        String workflow = Files.readString(IMAGE_WORKFLOW);
        assertTrue(workflow.contains("tests/integration/admin-bootstrap-mysql84.sh"));
    }
}
