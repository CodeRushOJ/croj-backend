package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.vo.SubmissionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提交记录Mapper接口
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * 获取用户在某题目的最佳提交
     *
     * @param userId 用户ID
     * @param problemId 题目ID
     * @return 最佳提交（通过的提交中运行时间最短的）
     */
    Submission getUserBestSubmission(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /**
     * 获取用户题目通过状态
     *
     * @param userId 用户ID
     * @param problemId 题目ID
     * @return 状态，1-已通过，2-未通过，没有记录则返回NULL
     */
    Integer getUserProblemStatus(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /**
     * 查询提交详情列表（包含用户和问题信息）
     *
     * @param page 分页对象
     * @param userId 用户ID，可为null
     * @param problemId 题目ID，可为null
     * @param language 编程语言，可为null
     * @param status 状态，可为null
     * @return 提交详情分页对象
     */
    IPage<SubmissionVO> getSubmissionList(Page<SubmissionVO> page,
                                          @Param("userId") Long userId,
                                          @Param("problemId") Long problemId,
                                          @Param("language") String language,
                                          @Param("status") Integer status);

    /**
     * 获取用户的提交统计
     *
     * @param userId 用户ID
     * @return 提交统计
     */
    int countUserSubmissions(@Param("userId") Long userId);

    /**
     * 获取用户的通过题目数
     *
     * @param userId 用户ID
     * @return 通过题目数
     */
    int countUserAcceptedProblems(@Param("userId") Long userId);
}