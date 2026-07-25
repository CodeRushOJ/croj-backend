package com.zephyr.croj.problem.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.config.properties.TestBundleProperties;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.dto.ProblemCreateDTO;
import com.zephyr.croj.model.entity.TestBundle;
import com.zephyr.croj.problem.ProblemVersionPublicationService;
import com.zephyr.croj.problem.TestBundleArchiveWriter;
import com.zephyr.croj.problem.TestBundleService;
import com.zephyr.croj.service.ProblemService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-bundle", name = "enabled", havingValue = "true")
public class ProblemImportService {
    private final ProblemPackageReader reader;
    private final ProblemImportStagingStorage staging;
    private final ProblemImportJobStore jobs;
    private final ProblemService problems;
    private final ProblemVersionMapper versions;
    private final TestBundleService bundles;
    private final ProblemVersionPublicationService publication;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ProblemImportService(
            ProblemPackageReader reader,
            ProblemImportStagingStorage staging,
            ProblemImportJobStore jobs,
            ProblemService problems,
            ProblemVersionMapper versions,
            TestBundleService bundles,
            ProblemVersionPublicationService publication,
            ObjectMapper objectMapper) {
        this(reader, staging, jobs, problems, versions, bundles, publication, objectMapper, Clock.systemUTC());
    }

    public ProblemImportResponses.Preflight preflight(long actorId, String filename, byte[] packageBytes) {
        ProblemImportBatch batch = reader.read(filename, packageBytes);
        List<ProblemImportResponses.ProblemPreview> previews = previews(batch);
        List<String> errors = previews.stream().flatMap(item -> item.errors().stream()).distinct().toList();
        int testCases = batch.problems().stream().mapToInt(problem -> problem.tests().size()).sum();
        String sha256 = sha256(packageBytes);
        if (!errors.isEmpty()) {
            return new ProblemImportResponses.Preflight(
                    null, batch.format().name(), sha256, batch.problems().size(), testCases,
                    errors, batch.warnings(), previews);
        }

        String jobId = UUID.randomUUID().toString();
        String objectKey = staging.put(jobId, packageBytes, sha256);
        ProblemImportResponses.Preflight response = new ProblemImportResponses.Preflight(
                jobId, batch.format().name(), sha256, batch.problems().size(), testCases,
                List.of(), batch.warnings(), previews);
        Instant now = clock.instant();
        jobs.create(new ProblemImportJob(
                jobId,
                actorId,
                "VALIDATED",
                batch.format().name(),
                sha256,
                objectKey,
                toJson(response),
                0,
                now,
                now.plus(24, ChronoUnit.HOURS),
                null));
        return response;
    }

    @Transactional
    public ProblemImportResponses.Commit commit(long actorId, String jobId) {
        Instant now = clock.instant();
        ProblemImportJob job = jobs.lockOwned(jobId, actorId, now);
        if ("COMMITTED".equals(job.status())) {
            return new ProblemImportResponses.Commit(job.id(), job.status(), job.importedCount());
        }
        if (!"VALIDATED".equals(job.status())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "problem import job cannot be committed");
        }
        byte[] packageBytes = staging.get(job.stagingObjectKey());
        if (!job.fileSha256().equals(sha256(packageBytes))) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "staged problem package digest mismatch");
        }
        ProblemImportBatch batch = reader.read(job.stagingObjectKey(), packageBytes);
        List<ProblemImportResponses.ProblemPreview> checked = previews(batch);
        if (checked.stream().anyMatch(item -> !item.errors().isEmpty())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "problem package no longer passes validation");
        }

        int imported = 0;
        for (ProblemImportDraft draft : batch.problems()) {
            long problemId = problems.createProblem(toCreateRequest(draft), actorId);
            Long versionId = versions.findLatestDraftVersionId(problemId);
            if (versionId == null) {
                throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
            }
            BuiltBundle bundle = buildBundle(
                    draft.tests(),
                    draft.timeLimitMillis(),
                    memoryMegabytes(draft.memoryLimitKilobytes()));
            TestBundle attached = bundles.attach(problemId, versionId, bundle.archive(), bundle.manifest());
            if (attached == null) {
                throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
            }
            publication.publish(problemId, versionId);
            imported++;
        }
        jobs.markCommitted(job.id(), imported, now);
        return new ProblemImportResponses.Commit(job.id(), "COMMITTED", imported);
    }

    private List<ProblemImportResponses.ProblemPreview> previews(ProblemImportBatch batch) {
        List<ProblemImportResponses.ProblemPreview> result = new ArrayList<>();
        for (int index = 0; index < batch.problems().size(); index++) {
            ProblemImportDraft draft = batch.problems().get(index);
            List<String> errors = new ArrayList<>();
            requireText(draft.title(), "title", errors);
            requireText(draft.description(), "description", errors);
            requireText(draft.inputDescription(), "input description", errors);
            requireText(draft.outputDescription(), "output description", errors);
            if (draft.tests().isEmpty()) {
                errors.add("problem has no hidden test cases");
            }
            if (draft.tests().size() > TestBundleProperties.V1_MAX_CASES) {
                errors.add("judge manifest v1 supports at most 256 hidden test cases");
            }
            if (!caseContentsFit(
                    draft.tests(),
                    TestBundleProperties.V1_MAX_BUNDLE_BYTES
                            - TestBundleProperties.V1_MAX_MANIFEST_BYTES)) {
                errors.add("hidden test contents exceed the judge manifest v1 capacity");
            }
            if (draft.codeResources().stream().anyMatch(resource ->
                    resource.kind() == ProblemImportCodeKind.SPECIAL_JUDGE
                            || resource.kind() == ProblemImportCodeKind.TESTLIB_JUDGE
                            || resource.kind() == ProblemImportCodeKind.INTERACTOR)) {
                errors.add("special judges and interactors are not supported by judge manifest v1");
            }
            if (draft.timeLimitMillis() < 1 || draft.timeLimitMillis() > 10_000) {
                errors.add("time limit must be between 1 and 10000 milliseconds");
            }
            int memoryMb = memoryMegabytes(draft.memoryLimitKilobytes());
            if (memoryMb < 1 || memoryMb > 1024) {
                errors.add("memory limit must be between 1 and 1024 megabytes");
            }
            String sourceId = firstNonBlank(draft.remoteId(), draft.upstreamUrl(), "item-" + (index + 1));
            result.add(new ProblemImportResponses.ProblemPreview(
                    sourceId,
                    draft.title(),
                    draft.tests().size(),
                    errors.isEmpty() ? "READY" : "ERROR",
                    errors,
                    List.of()));
        }
        return result;
    }

    private ProblemCreateDTO toCreateRequest(ProblemImportDraft draft) {
        ProblemCreateDTO request = new ProblemCreateDTO();
        request.setTitle(draft.title());
        request.setDescription(draft.description());
        request.setInputDescription(draft.inputDescription());
        request.setOutputDescription(draft.outputDescription());
        request.setHints(draft.hint() == null || draft.hint().isBlank() ? List.of() : List.of(draft.hint()));
        request.setSamples(draft.samples().stream().map(sample -> Map.of(
                "input", sample.input(),
                "output", sample.output())).toList());
        request.setTimeLimit(draft.timeLimitMillis());
        request.setMemoryLimit(memoryMegabytes(draft.memoryLimitKilobytes()));
        request.setDifficulty(2);
        request.setJudgeMode(0);
        request.setTotalScore(100);
        request.setStatus(1);
        request.setSource(draft.source());
        draft.codeResources().stream()
                .filter(resource -> resource.kind() == ProblemImportCodeKind.SPECIAL_JUDGE
                        || resource.kind() == ProblemImportCodeKind.TESTLIB_JUDGE)
                .findFirst()
                .ifPresent(resource -> {
                    request.setIsSpecialJudge(true);
                    request.setSpecialJudgeCode(resource.content());
                    request.setSpecialJudgeLanguage(resource.language());
                });
        return request;
    }

    private BuiltBundle buildBundle(
            List<ProblemImportCase> cases,
            int timeLimitMillis,
            int memoryLimitMiB) {
        List<Map<String, Object>> manifestCases = new ArrayList<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int index = 0; index < cases.size(); index++) {
            int id = index + 1;
            String inputPath = "cases/%d.in".formatted(id);
            String outputPath = "cases/%d.out".formatted(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Integer.toString(id));
            item.put("input", inputPath);
            item.put("output", outputPath);
            item.put("weight", 1);
            manifestCases.add(item);
            files.put(inputPath, cases.get(index).input().getBytes(StandardCharsets.UTF_8));
            files.put(outputPath, cases.get(index).output().getBytes(StandardCharsets.UTF_8));
        }
        Map<String, Object> manifestRoot = new LinkedHashMap<>();
        manifestRoot.put("schemaVersion", 1);
        manifestRoot.put("judgeMode", "ACM");
        manifestRoot.put("checker", "exact");
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("timeLimitMillis", timeLimitMillis);
        limits.put("memoryLimitMiB", memoryLimitMiB);
        manifestRoot.put("limits", limits);
        manifestRoot.put("cases", manifestCases);
        String manifest = toJson(manifestRoot);
        return new BuiltBundle(new TestBundleArchiveWriter().write(manifest, files), manifest);
    }

    static boolean caseContentsFit(List<ProblemImportCase> cases, long maximumBytes) {
        if (maximumBytes < 0) {
            return false;
        }
        long total = 0;
        try {
            for (ProblemImportCase testCase : cases) {
                if (testCase.input() == null || testCase.output() == null) {
                    return false;
                }
                total = Math.addExact(
                        total,
                        testCase.input().getBytes(StandardCharsets.UTF_8).length);
                total = Math.addExact(
                        total,
                        testCase.output().getBytes(StandardCharsets.UTF_8).length);
                if (total > maximumBytes) {
                    return false;
                }
            }
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private int memoryMegabytes(int kilobytes) {
        return Math.toIntExact((kilobytes + 1023L) / 1024L);
    }

    private void requireText(String value, String field, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("at least one value is required");
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_ERROR);
        }
    }

    private record BuiltBundle(byte[] archive, String manifest) {
    }
}
