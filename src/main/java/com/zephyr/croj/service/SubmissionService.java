package com.zephyr.croj.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zephyr.croj.model.dto.SubmissionDTO;
import com.zephyr.croj.model.dto.SubmissionQueryDTO;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.vo.SubmissionVO;

/**
 * 提交记录服务接口
 */
public interface SubmissionService extends IService<Submission> {

    /**
     * 提交代码
     *
     * @param dto 提交参数
     * @param userId 当前用户ID
     * @return 提交ID
     */
    Long submitCode(SubmissionDTO dto, Long userId);

    /**
     * 获取提交详情
     *
     * @param id 提交ID
     * @param userId 当前用户ID
     * @return 提交详情
     */
    SubmissionVO getSubmissionById(Long id, Long userId);

    /**
     * 查询提交列表
     *
     * @param queryDTO 查询条件
     * @param userId 当前用户ID
     * @return 提交列表
     */
    IPage<SubmissionVO> getSubmissionList(SubmissionQueryDTO queryDTO, Long userId);

    /**
     * 模拟判题（仅在没有实际判题系统时使用）
     *
     * @param submissionId 提交ID
     */
    void mockJudge(Long submissionId);

    /**
     * 获取用户在某题目的最佳提交
     *
     * @param userId 用户ID
     * @param problemId 题目ID
     * @return 最佳提交
     */
    SubmissionVO getUserBestSubmission(Long userId, Long problemId);

    /**
     * 获取用户的提交统计
     *
     * @param userId 用户ID
     * @return 提交统计
     */
    int countUserSubmissions(Long userId);

    /**
     * 获取用户的通过题目数
     *
     * @param userId 用户ID
     * @return 通过题目数
     */
    int countUserAcceptedProblems(Long userId);
}