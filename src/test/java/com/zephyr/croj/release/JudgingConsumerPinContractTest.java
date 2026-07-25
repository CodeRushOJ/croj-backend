package com.zephyr.croj.release;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JudgingConsumerPinContractTest {

    private static final String FINAL_JUDGING_COMMIT =
            "c38a509754971c5078aa15296f7126f6777daba3";

    @Test
    void localAndGithubContractsUseTheReviewedFinalJudgingCommit() throws IOException {
        String script = Files.readString(Path.of("scripts", "verify-test-bundle-contract.sh"));
        String workflow = Files.readString(
                Path.of(".github", "workflows", "test-bundle-contract.yml"));
        String readme = Files.readString(Path.of("README.md"));

        assertTrue(script.contains("JUDGING_CONTRACT_COMMIT=\"" + FINAL_JUDGING_COMMIT + "\""));
        assertTrue(workflow.contains("ref: " + FINAL_JUDGING_COMMIT));
        assertTrue(workflow.contains("Backend producer to Judging " + FINAL_JUDGING_COMMIT.substring(0, 7)));
        assertTrue(readme.contains("`" + FINAL_JUDGING_COMMIT.substring(0, 7) + "`"));
    }
}
