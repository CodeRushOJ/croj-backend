package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.vo.ProblemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目Mapper接口
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 获取题目列表，带标签和用户提交信息
     *
     * @param page 分页参数
     * @param keyword 关键字
     * @param difficulty 难度
     * @param status 状态
     * @param tagIds 标签ID列表
     * @param userId 当前用户ID
     * @return 题目列表
     */
    IPage<ProblemVO> getProblemList(Page<ProblemVO> page,
                                    @Param("keyword") String keyword,
                                    @Param("difficulty") Integer difficulty,
                                    @Param("status") Integer status,
                                    @Param("tagIds") List<Long> tagIds,
                                    @Param("userId") Long userId,
                                    @Param("canManageAll") boolean canManageAll);

    @Select("SELECT * FROM t_problem WHERE id=#{problemId} AND is_deleted=0 FOR UPDATE")
    Problem selectForUpdate(@Param("problemId") Long problemId);

    @Select("""
            SELECT CASE
                     WHEN COUNT(*) = 0 THEN 0
                     WHEN SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) > 0 THEN 1
                     ELSE 2
                   END
            FROM t_submission
            WHERE problem_id = #{problemId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int getUserSubmitStatus(
            @Param("problemId") Long problemId,
            @Param("userId") Long userId);

    /**
     * 通过题目编号查询题目
     *
     * @param problemNo 题目编号
     * @return 题目
     */
    Problem getProblemByNo(@Param("problemNo") String problemNo);

    /**
     * 更新题目提交数
     *
     * @param problemId 题目ID
     * @return 影响行数
     */
    int incrementSubmitCount(@Param("problemId") Long problemId);

    /**
     * 更新题目通过数
     *
     * @param problemId 题目ID
     * @return 影响行数
     */
    int incrementAcceptedCount(@Param("problemId") Long problemId);
}
