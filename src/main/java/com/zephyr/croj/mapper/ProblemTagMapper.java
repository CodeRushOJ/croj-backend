package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.ProblemTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目标签Mapper接口
 */
@Mapper
public interface ProblemTagMapper extends BaseMapper<ProblemTag> {

    /**
     * 查询题目关联的标签列表
     *
     * @param problemId 题目ID
     * @return 标签列表
     */
    List<ProblemTag> getTagsByProblemId(@Param("problemId") Long problemId);

    /**
     * 批量查询题目关联的标签
     *
     * @param problemIds 题目ID列表
     * @return 题目ID到标签列表的映射
     */
    List<ProblemTag> getTagsByProblemIds(@Param("problemIds") List<Long> problemIds);
}