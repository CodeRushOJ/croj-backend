package com.zephyr.croj.common.exception;

import com.zephyr.croj.common.enums.ResultCodeEnum;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常类
 *
 
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造函数
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        this.code = ResultCodeEnum.ERROR.getCode();
        this.message = message;
    }

    /**
     * 构造函数
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public BusinessException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 构造函数
     *
     * @param resultCodeEnum 结果枚举
     */
    public BusinessException(ResultCodeEnum resultCodeEnum) {
        this.code = resultCodeEnum.getCode();
        this.message = resultCodeEnum.getMessage();
    }
}