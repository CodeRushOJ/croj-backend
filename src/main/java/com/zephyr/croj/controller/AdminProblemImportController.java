package com.zephyr.croj.controller;

import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.problem.importer.ProblemImportResponses;
import com.zephyr.croj.problem.importer.ProblemImportService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/admin/problem-imports")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-bundle", name = "enabled", havingValue = "true")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminProblemImportController {
    private final ProblemImportService imports;
    private final HttpServletRequest request;

    @PostMapping(value = "/preflight", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<ProblemImportResponses.Preflight> preflight(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR);
        }
        try {
            return Result.success(imports.preflight(
                    actorId(),
                    file.getOriginalFilename() == null ? "package" : file.getOriginalFilename(),
                    file.getBytes()));
        } catch (IOException exception) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "problem package upload cannot be read");
        }
    }

    @PostMapping("/{jobId}/commit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<ProblemImportResponses.Commit> commit(@PathVariable String jobId) {
        return Result.success(imports.commit(actorId(), jobId));
    }

    private long actorId() {
        Object actor = request.getAttribute("userId");
        if (!(actor instanceof Long actorId)) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }
        return actorId;
    }
}
