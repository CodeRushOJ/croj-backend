package com.zephyr.croj.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
/**
 * 问题标签视图对象
 */
@Data
public class ProblemTagVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签颜色（十六进制颜色码）
     */
    private String color;
}