package com.zephyr.croj.common.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

import com.zephyr.croj.common.enums.ResultCodeEnum;

/**
 * 统一API响应结果
 *
 
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 返回消息
     */
    private String message;

    /**
     * 返回数据
     */
    private T data;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 私有构造方法，禁止直接创建
     */
    private Result() {
    }

    /**
     * 通用返回成功
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 返回成功带数据
     */
    public static <T> Result<T> success(T data) {
        return success(ResultCodeEnum.SUCCESS.getMessage(), data);
    }

    /**
     * 返回成功带消息和数据
     */
    public static <T> Result<T> success(String message, T data) {
        return build(ResultCodeEnum.SUCCESS.getCode(), message, data, true);
    }

    /**
     * 通用返回失败
     */
    public static <T> Result<T> error() {
        return error(ResultCodeEnum.ERROR.getMessage());
    }

    /**
     * 返回失败带消息
     */
    public static <T> Result<T> error(String message) {
        return error(ResultCodeEnum.ERROR.getCode(), message);
    }

    /**
     * 返回失败带状态码和消息
     */
    public static <T> Result<T> error(Integer code, String message) {
        return build(code, message, null, false);
    }

    /**
     * 构建结果
     */
    public static <T> Result<T> build(Integer code, String message, T data, Boolean success) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        result.setSuccess(success);
        return result;
    }

    /**
     * 使用ResultCodeEnum构建
     */
    public static <T> Result<T> build(ResultCodeEnum resultCodeEnum) {
        return build(resultCodeEnum.getCode(), resultCodeEnum.getMessage(), null,
                ResultCodeEnum.SUCCESS.getCode().equals(resultCodeEnum.getCode()));
    }

    /**
     * 使用ResultCodeEnum构建并携带数据
     */
    public static <T> Result<T> build(ResultCodeEnum resultCodeEnum, T data) {
        return build(resultCodeEnum.getCode(), resultCodeEnum.getMessage(), data,
                ResultCodeEnum.SUCCESS.getCode().equals(resultCodeEnum.getCode()));
    }
}