package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.JudgeResultReceipt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JudgeResultReceiptMapper extends BaseMapper<JudgeResultReceipt> {
    @Insert("""
            INSERT IGNORE INTO t_judge_result_receipt
              (result_id, submission_id, attempt_no, payload_sha256, final_status)
            VALUES
              (#{resultId}, #{submissionId}, #{attemptNo}, #{payloadSha256}, #{finalStatus})
            """)
    int insertIgnore(JudgeResultReceipt receipt);
}
