package com.zephyr.croj.service;

import com.zephyr.croj.model.dto.JudgeResultRequest;
import com.zephyr.croj.model.vo.JudgeResultResponse;

public interface JudgeResultService {
    JudgeResultResponse ingest(JudgeResultRequest request);
}
