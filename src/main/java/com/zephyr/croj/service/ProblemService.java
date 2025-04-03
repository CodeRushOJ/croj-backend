package com.zephyr.croj.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zephyr.croj.model.dto.ProblemCreateDTO;
import com.zephyr.croj.model.dto.ProblemQueryDTO;
import com.zephyr.croj.model.dto.ProblemUpdateDTO;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.vo.ProblemListItemVO;
import com.zephyr.croj.model.vo.ProblemVO;

/**
 * 问题服务接口
 */
public interface ProblemService extends IService<Problem> {

    /**
     * 创建问题
     *
     * @param dto 问题创建信息
     * @param userId 当前用户ID
     * @return 问题ID
     */
    Long createProblem(ProblemCreateDTO dto, Long userId);

    /**
     * 更新问题
     *
     * @param dto 问题更新信息
     * @param userId 当前用户ID
     * @return 是否成功
     */
    boolean updateProblem(ProblemUpdateDTO dto, Long userId);

    /**
     * 删除问题
     *
     * @param id 问题ID
     * @param userId 当前用户ID
     * @return 是否成功
     */
    boolean deleteProblem(Long id, Long userId);

    /**
     * 获取问题详情
     *
     * @param id 问题ID
     * @param userId 当前用户ID
     * @return 问题详情
     */
    ProblemVO getProblemById(Long id, Long userId);

    /**
     * A通过题目编号获取问题详情
     *
     * @param problemNo 题目编号
     * @param userId 当前用户ID
     * @return 问题详情
     */
    ProblemVO getProblemByNo(String problemNo, Long userId);

    /**
     * 获取问题列表
     *
     * @param queryDTO 查询条件
     * @param userId 当前用户ID
     * @return 问题列表
     */
    IPage<ProblemListItemVO> getProblemList(ProblemQueryDTO queryDTO, Long userId);

    /**
     * 更新问题提交数
     *
     * @param problemId 问题ID
     * @return 是否成功
     */
    boolean incrementSubmitCount(Long problemId);

    /**
     * 更新问题通过数
     *
     * @param problemId 问题ID
     * @return 是否成功
     */
    boolean incrementAcceptedCount(Long problemId);

    /**
     * 检查用户是否有权限操作问题
     *
     * @param problemId 问题ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    boolean checkPermission(Long problemId, Long userId);
}