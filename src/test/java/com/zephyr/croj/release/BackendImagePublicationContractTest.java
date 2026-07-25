package com.zephyr.croj.release;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BackendImagePublicationContractTest {

    @Test
    void signedTagPublicationExportsTheImmutableManifestDigest() throws IOException {
        String workflow = Files.readString(Path.of(".github", "workflows", "image.yml"));

        assertTrue(workflow.contains("id: push"));
        assertTrue(workflow.contains("IMAGE_DIGEST: ${{ steps.push.outputs.digest }}"));
        assertTrue(workflow.contains("backend-image.json"));
        assertTrue(workflow.contains("\"linux/amd64\""));
        assertTrue(workflow.contains("\"linux/arm64\""));
        assertTrue(workflow.contains("name: backend-image-${{ github.sha }}"));
    }
}
