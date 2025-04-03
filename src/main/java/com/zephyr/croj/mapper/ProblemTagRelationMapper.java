package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.ProblemTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目-标签关联Mapper接口
 */
@Mapper
public interface ProblemTagRelationMapper extends BaseMapper<ProblemTagRelation> {

    /**
     * 批量插入题目标签关联
     *
     * @param problemId 题目ID
     * @param tagIds 标签ID列表
     * @return 影响行数
     */
    int batchInsert(@Param("problemId") Long problemId, @Param("tagIds") List<Long> tagIds);

    /**
     * 删除题目的所有标签关联
     *
     * @param problemId 题目ID
     * @return 影响行数
     */
    int deleteByProblemId(@Param("problemId") Long problemId);

    /**
     * 获取使用了指定标签的题目数量
     *
     * @param tagId 标签ID
     * @return 题目数量
     */
    int countProblemsByTagId(@Param("tagId") Long tagId);
}