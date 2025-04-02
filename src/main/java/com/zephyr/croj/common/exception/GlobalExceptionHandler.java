package com.zephyr.croj.common.exception;

import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Set;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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