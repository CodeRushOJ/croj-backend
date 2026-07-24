package com.zephyr.croj.common.exception;

import com.zephyr.croj.announcement.AnnouncementApiException;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.contest.ContestApiException;
import com.zephyr.croj.problem.importer.ProblemPackageParseException;
import com.zephyr.croj.problem.TestBundleApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AnnouncementApiException.class)
    public ResponseEntity<Result<Void>> handleAnnouncementApiException(AnnouncementApiException exception) {
        log.warn("公告请求失败: {}", exception.getMessage());
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(exception.getStatus().value() * 100, exception.getMessage()));
    }

    @ExceptionHandler(ContestApiException.class)
    public ResponseEntity<Result<Void>> handleContestApiException(ContestApiException exception) {
        log.warn("比赛请求失败: {}", exception.getMessage());
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(exception.getStatus().value() * 100, exception.getMessage()));
    }

    @ExceptionHandler(TestBundleApiException.class)
    public ResponseEntity<Result<Void>> handleTestBundleApiException(TestBundleApiException exception) {
        log.warn("TestBundle request failed: {}", exception.getMessage());
        return ResponseEntity.status(exception.getStatus())
                .body(Result.error(exception.getStatus().value() * 100, exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        log.warn("Multipart upload exceeded the configured limit");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(41300, "uploaded archive exceeds the configured request limit"));
    }

    @ExceptionHandler(JudgeResultConflictException.class)
    public ResponseEntity<Result<Void>> handleJudgeResultConflict(JudgeResultConflictException exception) {
        log.warn("判题结果冲突: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(40900, exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("访问被拒绝: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(ResultCodeEnum.FORBIDDEN.getCode(), "无权限操作"));
    }

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder errorMsg = new StringBuilder();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            errorMsg.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage()).append(", ");
        }
        String message = !errorMsg.isEmpty() ? errorMsg.substring(0, errorMsg.length() - 2) : "参数错误";
        log.error("参数校验异常: {}", message);
        return Result.error(ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理参数校验异常 (@Validated)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        StringBuilder errorMsg = new StringBuilder();
        for (ConstraintViolation<?> violation : violations) {
            errorMsg.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append(", ");
        }
        String message = !errorMsg.isEmpty() ? errorMsg.substring(0, errorMsg.length() - 2) : "参数错误";
        log.error("参数校验异常: {}", message);
        return Result.error(ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder errorMsg = new StringBuilder();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            errorMsg.append(fieldError.getField()).append(": ").append(fieldError.getDefaultMessage()).append(", ");
        }
        String message = !errorMsg.isEmpty() ? errorMsg.substring(0, errorMsg.length() - 2) : "参数错误";
        log.error("参数绑定异常: {}", message);
        return Result.error(ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        ServletRequestBindingException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMalformedRequest(Exception exception) {
        log.warn("请求格式错误: {}", exception.getMessage());
        return Result.error(ResultCodeEnum.PARAM_ERROR.getCode(), "请求格式错误");
    }

    @ExceptionHandler(ProblemPackageParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleProblemPackageParseException(ProblemPackageParseException exception) {
        log.warn("题目包解析失败: {}", exception.getMessage());
        return Result.error(ResultCodeEnum.PARAM_ERROR.getCode(), "题目包格式错误或超出限制");
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(ResultCodeEnum.ERROR.getCode(), "服务器内部错误，请联系管理员");
    }
}
