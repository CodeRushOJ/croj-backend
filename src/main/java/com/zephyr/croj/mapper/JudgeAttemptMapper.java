package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.JudgeAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JudgeAttemptMapper extends BaseMapper<JudgeAttempt> {
    @Update("""
            UPDATE t_judge_attempt
            SET status = #{status}, result_json = #{resultJson},
                started_at = COALESCE(started_at, CURRENT_TIMESTAMP(3)),
                finished_at = CURRENT_TIMESTAMP(3)
            WHERE submission_id = #{submissionId} AND attempt_no = #{attemptNo}
              AND status IN ('QUEUED', 'RUNNING')
            """)
    int completeAttempt(
            @Param("submissionId") long submissionId,
            @Param("attemptNo") int attemptNo,
            @Param("status") String status,
            @Param("resultJson") String resultJson);
}
