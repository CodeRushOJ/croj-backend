package com.zephyr.croj.model.projection;

public record ProblemTagProjection(
        long problemId,
        long tagId,
        String name,
        String color) {}
