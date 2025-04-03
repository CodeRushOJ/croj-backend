package com.zephyr.croj.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zephyr.croj.model.entity.ProblemTag;
import com.zephyr.croj.model.vo.ProblemTagVO;

import java.util.List;

/**
 * 问题标签服务接口
 */
public interface ProblemTagService extends IService<ProblemTag> {

    /**
     * 创建标签
     *
     * @param tag 标签信息
     * @param userId 当前用户ID
     * @return 标签ID
     */
    Long createTag(ProblemTag tag, Long userId);

    /**
     * 更新标签
     *
     * @param tag 标签信息
     * @param userId 当前用户ID
     * @return 是否成功
     */
    boolean updateTag(ProblemTag tag, Long userId);

    /**
     * 删除标签
     *
     * @param id 标签ID
     * @param userId 当前用户ID
     * @return 是否成功
     */
    boolean deleteTag(Long id, Long userId);

    /**
     * 获取标签列表
     *
     * @return 标签列表
     */
    List<ProblemTagVO> getAllTags();

    /**
     * 获取标签列表（分页）
     *
     * @param current 当前页
     * @param size 每页大小
     * @param keyword 关键词
     * @return 标签分页列表
     */
    IPage<ProblemTagVO> getTagList(long current, long size, String keyword);

    /**
     * 根据ID获取标签
     *
     * @param id 标签ID
     * @return 标签信息
     */
    ProblemTagVO getTagById(Long id);

    /**
     * 获取题目的标签列表
     *
     * @param problemId 题目ID
     * @return 标签列表
     */
    List<ProblemTagVO> getTagsByProblemId(Long problemId);

    /**
     * 批量获取题目的标签
     *
     * @param problemIds 题目ID列表
     * @return 题目ID到标签列表的映射
     */
    List<ProblemTagVO> getTagsByProblemIds(List<Long> problemIds);

    /**
     * 保存题目标签关联
     *
     * @param problemId 题目ID
     * @param tagIds 标签ID列表
     * @return 是否成功
     */
    boolean saveProblemTags(Long problemId, List<Long> tagIds);

    /**
     * 删除题目标签关联
     *
     * @param problemId 题目ID
     * @return 是否成功
     */
    boolean deleteProblemTags(Long problemId);
}