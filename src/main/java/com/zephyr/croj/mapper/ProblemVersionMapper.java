package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.ProblemVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProblemVersionMapper extends BaseMapper<ProblemVersion> {
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM t_problem_version WHERE problem_id = #{problemId}")
    int findLatestVersionNumber(@Param("problemId") Long problemId);

    @Select("""
            SELECT COUNT(*) > 0 FROM t_problem_version pv
            JOIN t_test_bundle tb ON tb.problem_version_id=pv.id
            WHERE pv.problem_id=#{problemId} AND pv.id=#{versionId} AND pv.state='PUBLISHED'
            """)
    boolean isJudgeReady(
            @Param("problemId") Long problemId,
            @Param("versionId") Long versionId);
}
