package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.config.properties.TestBundleProperties;
import com.zephyr.croj.problem.AdminTestBundleService;
import com.zephyr.croj.problem.TestBundleApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/admin/problems/{problemId}/versions/{versionId}/test-bundle")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-bundle", name = "enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Tag(name = "Admin TestBundle", description = "Upload and publish private TestBundle v1/v2 archives")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminTestBundleController {
    private final AdminTestBundleService testBundles;
    private final TestBundleProperties properties;

    @GetMapping
    @Operation(summary = "Read TestBundle metadata and the current strong ETag")
    public ResponseEntity<Result<AdminTestBundleService.View>> describe(
            @PathVariable long problemId,
            @PathVariable long versionId) {
        return response(testBundles.describe(problemId, versionId));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Validate and attach a TestBundle v1/v2 ZIP to a draft problem version",
            requestBody = @RequestBody(required = true, content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "object"))))
    public ResponseEntity<Result<AdminTestBundleService.View>> upload(
            @PathVariable long problemId,
            @PathVariable long versionId,
            @Parameter(description = "Strong ETag returned by the metadata endpoint", required = true)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch,
            @RequestPart("file") MultipartFile file) {
        requirePrecondition(ifMatch);
        if (file.isEmpty()) {
            throw TestBundleApiException.badRequest("test bundle archive must not be empty");
        }
        if (file.getSize() > properties.getMaxArchiveBytes()) {
            throw TestBundleApiException.payloadTooLarge();
        }
        try {
            return response(testBundles.upload(problemId, versionId, ifMatch, file.getBytes()));
        } catch (IOException exception) {
            throw TestBundleApiException.badRequest("test bundle upload cannot be read");
        }
    }

    @PostMapping("/publish")
    @Operation(summary = "Atomically publish a problem version with its attached TestBundle")
    public ResponseEntity<Result<AdminTestBundleService.View>> publish(
            @PathVariable long problemId,
            @PathVariable long versionId,
            @Parameter(description = "Strong ETag returned after upload", required = true)
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch) {
        requirePrecondition(ifMatch);
        return response(testBundles.publish(problemId, versionId, ifMatch));
    }

    private void requirePrecondition(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw TestBundleApiException.preconditionRequired();
        }
    }

    private ResponseEntity<Result<AdminTestBundleService.View>> response(
            AdminTestBundleService.View view) {
        return ResponseEntity.ok()
                .eTag(view.etag())
                .body(Result.success(view));
    }
}
