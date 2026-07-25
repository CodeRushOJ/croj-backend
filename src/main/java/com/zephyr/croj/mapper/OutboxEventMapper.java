package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Update("""
            UPDATE t_outbox_event
               SET claimed_by = #{claimId}, claimed_at = UTC_TIMESTAMP(3)
             WHERE published_at IS NULL
               AND (next_attempt_at IS NULL OR next_attempt_at <= UTC_TIMESTAMP(3))
               AND (claimed_at IS NULL OR claimed_at < DATE_SUB(UTC_TIMESTAMP(3), INTERVAL #{claimTimeoutSeconds} SECOND))
             ORDER BY created_at
             LIMIT 1
            """)
    int claimNext(
            @Param("claimId") String claimId,
            @Param("claimTimeoutSeconds") long claimTimeoutSeconds);

    @Select("""
            SELECT * FROM t_outbox_event
             WHERE claimed_by = #{claimId} AND published_at IS NULL
             ORDER BY created_at
            """)
    OutboxEvent findClaimed(@Param("claimId") String claimId);

    @Update("""
            UPDATE t_outbox_event
               SET published_at = UTC_TIMESTAMP(3), claimed_by = NULL, claimed_at = NULL, last_error = NULL
             WHERE id = #{id} AND claimed_by = #{claimId} AND published_at IS NULL
            """)
    int markPublished(
            @Param("id") String id,
            @Param("claimId") String claimId);

    @Update("""
            UPDATE t_outbox_event
               SET attempts = attempts + 1,
                   next_attempt_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL #{delaySeconds} SECOND),
                   last_error = #{error},
                   claimed_by = NULL, claimed_at = NULL
             WHERE id = #{id} AND claimed_by = #{claimId} AND published_at IS NULL
            """)
    int releaseAfterFailure(
            @Param("id") String id,
            @Param("claimId") String claimId,
            @Param("delaySeconds") long delaySeconds,
            @Param("error") String error);
}
