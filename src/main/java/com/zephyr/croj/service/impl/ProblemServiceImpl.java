package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.enums.UserRoleEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.ProblemMapper;
import com.zephyr.croj.model.dto.ProblemCreateDTO;
import com.zephyr.croj.model.dto.ProblemQueryDTO;
import com.zephyr.croj.model.dto.ProblemUpdateDTO;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.ProblemListItemVO;
import com.zephyr.croj.model.vo.ProblemTagVO;
import com.zephyr.croj.model.vo.ProblemVO;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.ProblemTagService;
import com.zephyr.croj.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 问题服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    private final ProblemTagService problemTagService;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProblem(ProblemCreateDTO dto, Long userId) {
        // 检查用户是否有权限创建问题
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 只有管理员和超级管理员可以创建问题
        if (!UserRoleEnum.ADMIN.getCode().equals(user.getRole())
                && !UserRoleEnum.SUPER_ADMIN.getCode().equals(user.getRole())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 创建问题实体
        Problem problem = new Problem();
        BeanUtils.copyProperties(dto, problem);

        // 设置初始值
        problem.setSubmitCount(0);
        problem.setAcceptedCount(0);
        problem.setCreateUserId(userId);

        // 如果是OI模式但未设置总分，则设置默认总分100
        if (dto.getJudgeMode() == 1 && dto.getTotalScore() == null) {
            problem.setTotalScore(100);
        }

        // 生成题目编号 (格式: P1000, P1001, ...)
        String problemNo = generateProblemNo();
        problem.setProblemNo(problemNo);

        // 保存问题
        boolean saved = save(problem);
        if (!saved) {
            throw new BusinessException(ResultCodeEnum.CREATE_ERROR);
        }

        // 保存标签关联
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            problemTagService.saveProblemTags(problem.getId(), dto.getTagIds());
        }

        return problem.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProblem(ProblemUpdateDTO dto, Long userId) {
        // 检查问题是否存在
        Problem problem = getById(dto.getId());
        if (problem == null) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_EXIST);
        }

        // 检查是否有权限修改
        if (!checkPermission(problem.getId(), userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 更新问题
        BeanUtils.copyProperties(dto, problem);

        // 如果是OI模式但未设置总分，则设置默认总分100
        if (dto.getJudgeMode() != null && dto.getJudgeMode() == 1 && dto.getTotalScore() == null) {
            problem.setTotalScore(100);
        }

        boolean updated = updateById(problem);
        if (!updated) {
            throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
        }

        // 更新标签关联
        if (dto.getTagIds() != null) {
            problemTagService.deleteProblemTags(problem.getId());
            if (!dto.getTagIds().isEmpty()) {
                problemTagService.saveProblemTags(problem.getId(), dto.getTagIds());
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProblem(Long id, Long userId) {
        // 检查问题是否存在
        Problem problem = getById(id);
        if (problem == null) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_EXIST);
        }

        // 检查是否有权限删除
        if (!checkPermission(id, userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 删除问题（逻辑删除）
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(ResultCodeEnum.DELETE_ERROR);
        }

        // 删除标签关联
        problemTagService.deleteProblemTags(id);

        return true;
    }

    @Override
    public ProblemVO getProblemById(Long id, Long userId) {
        // 获取问题
        Problem problem = getById(id);
        if (problem == null) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_EXIST);
        }

        // 如果是非公开题目，检查用户是否有权限查看
        if (!problem.getStatus().equals(0) && !checkPermission(id, userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 转换为VO
        ProblemVO vo = convertToVO(problem);

        // 获取标签
        List<ProblemTagVO> tags = problemTagService.getTagsByProblemId(id);
        vo.setTags(tags);

        // 获取用户提交状态
        vo.setUserStatus(getUserSubmitStatus(id, userId));

        return vo;
    }

    @Override
    public ProblemVO getProblemByNo(String problemNo, Long userId) {
        // 获取问题
        Problem problem = baseMapper.getProblemByNo(problemNo);
        if (problem == null) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_EXIST);
        }

        // 如果是非公开题目，检查用户是否有权限查看
        if (!problem.getStatus().equals(0) && !checkPermission(problem.getId(), userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 转换为VO
        ProblemVO vo = convertToVO(problem);

        // 获取标签
        List<ProblemTagVO> tags = problemTagService.getTagsByProblemId(problem.getId());
        vo.setTags(tags);

        // 获取用户提交状态
        vo.setUserStatus(getUserSubmitStatus(problem.getId(), userId));

        return vo;
    }

    @Override
    public IPage<ProblemListItemVO> getProblemList(ProblemQueryDTO queryDTO, Long userId) {
        // 创建分页对象
        Page<ProblemVO> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());

        // 查询条件
        String keyword = queryDTO.getKeyword();
        Integer difficulty = queryDTO.getDifficulty();
        Integer status = queryDTO.getStatus();
        List<Long> tagIds = queryDTO.getTagIds();

        // 查询问题列表
        IPage<ProblemVO> problemPage = baseMapper.getProblemList(page, keyword, difficulty, status, tagIds, userId);

        // 获取所有问题ID
        List<Long> problemIds = problemPage.getRecords().stream()
                .map(ProblemVO::getId)
                .collect(Collectors.toList());

        // 批量获取标签
        List<ProblemTagVO> allTags = new ArrayList<>();
        if (!problemIds.isEmpty()) {
            allTags = problemTagService.getTagsByProblemIds(problemIds);
        }

        // 标签按问题ID分组
        Map<Long, List<ProblemTagVO>> tagMap = allTags.stream()
                .collect(Collectors.groupingBy(ProblemTagVO::getId));

        // 转换为列表项VO
        List<ProblemListItemVO> records = problemPage.getRecords().stream()
                .map(problem -> {
                    ProblemListItemVO itemVO = new ProblemListItemVO();
                    itemVO.setId(problem.getId());
                    itemVO.setProblemNo(problem.getProblemNo());
                    itemVO.setTitle(problem.getTitle());
                    itemVO.setDifficulty(problem.getDifficulty());
                    itemVO.setSubmitCount(problem.getSubmitCount());
                    itemVO.setAcceptedCount(problem.getAcceptedCount());

                    // 计算通过率
                    double acceptRate = 0.0;
                    if (problem.getSubmitCount() > 0) {
                        acceptRate = (double) problem.getAcceptedCount() / problem.getSubmitCount() * 100;
                        acceptRate = Math.round(acceptRate * 100) / 100.0; // 保留两位小数
                    }
                    itemVO.setAcceptRate(acceptRate);

                    // 设置标签
                    itemVO.setTags(tagMap.getOrDefault(problem.getId(), new ArrayList<>()));

                    // 设置用户提交状态
                    itemVO.setUserStatus(problem.getUserStatus());

                    return itemVO;
                })
                .collect(Collectors.toList());

        // 构造返回结果
        IPage<ProblemListItemVO> result = new Page<>(problemPage.getCurrent(), problemPage.getSize(), problemPage.getTotal());
        result.setRecords(records);

        return result;
    }

    @Override
    public boolean incrementSubmitCount(Long problemId) {
        return baseMapper.incrementSubmitCount(problemId) > 0;
    }

    @Override
    public boolean incrementAcceptedCount(Long problemId) {
        return baseMapper.incrementAcceptedCount(problemId) > 0;
    }

    @Override
    public boolean checkPermission(Long problemId, Long userId) {
        if (userId == null) {
            return false;
        }

        // 获取用户信息
        User user = userService.getById(userId);
        if (user == null) {
            return false;
        }

        // 超级管理员和管理员有权限操作所有问题
        if (UserRoleEnum.SUPER_ADMIN.getCode().equals(user.getRole())
                || UserRoleEnum.ADMIN.getCode().equals(user.getRole())) {
            return true;
        }

        // 普通用户只能操作自己创建的问题
        Problem problem = getById(problemId);
        return problem != null && userId.equals(problem.getCreateUserId());
    }

    /**
     * 获取用户提交状态
     *
     * @param problemId 问题ID
     * @param userId 用户ID
     * @return 提交状态（0-未提交，1-已通过，2-未通过）
     */
    private Integer getUserSubmitStatus(Long problemId, Long userId) {
        if (userId == null) {
            return 0;
        }

        // 这里应该查询用户提交记录表得到状态
        // 由于提交记录相关功能还未实现，这里先返回0
        return 0;
    }

    /**
     * 将问题实体转换为VO
     *
     * @param problem 问题实体
     * @return 问题VO
     */
    private ProblemVO convertToVO(Problem problem) {
        ProblemVO vo = new ProblemVO();
        BeanUtils.copyProperties(problem, vo);
        return vo;
    }

    /**
     * 生成题目编号
     *
     * @return 题目编号
     */
    private String generateProblemNo() {
        // 获取最大的题目编号
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Problem::getProblemNo);
        wrapper.last("LIMIT 1");
        Problem latestProblem = getOne(wrapper);

        int nextNumber = 1000; // 默认起始编号
        if (latestProblem != null && latestProblem.getProblemNo() != null) {
            try {
                String numberStr = latestProblem.getProblemNo().substring(1); // 去掉前缀'P'
                nextNumber = Integer.parseInt(numberStr) + 1;
            } catch (Exception e) {
                log.error("生成题目编号失败", e);
            }
        }

        return "P" + nextNumber;
    }
}