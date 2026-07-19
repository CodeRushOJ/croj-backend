package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.enums.SubmissionStatusEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.contest.ContestService;
import com.zephyr.croj.mapper.SubmissionMapper;
import com.zephyr.croj.mapper.JudgeAttemptMapper;
import com.zephyr.croj.mapper.ProblemVersionMapper;
import com.zephyr.croj.model.dto.SubmissionDTO;
import com.zephyr.croj.model.dto.SubmissionQueryDTO;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.entity.JudgeAttempt;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.SubmissionVO;
import com.zephyr.croj.outbox.SubmissionOutbox;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.SubmissionService;
import com.zephyr.croj.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 提交记录服务实现类
 */
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final UserService userService;
    private final ProblemService problemService;
    private final SubmissionOutbox submissionOutbox;
    private final JudgeAttemptMapper judgeAttempts;
    private final ContestService contestService;
    private final ProblemVersionMapper problemVersions;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitCode(SubmissionDTO dto, Long userId) {
        // 检查用户是否存在
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 检查题目是否存在
        Problem problem = problemService.getById(dto.getProblemId());
        if (problem == null) {
            throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_EXIST);
        }

        // 如果是非公开题目，检查用户是否有权限提交
        if (!problem.getStatus().equals(0) && !problemService.checkPermission(problem.getId(), userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 创建提交记录
        Submission submission = new Submission();
        submission.setProblemId(dto.getProblemId());
        if (dto.getContestId() != null) {
            submission.setContestId(dto.getContestId());
            submission.setProblemVersionId(
                    contestService.validateSubmission(dto.getContestId(), userId, dto.getProblemId()));
        } else {
            Long publishedVersionId = problem.getPublishedVersionId();
            if (publishedVersionId == null
                    || !problemVersions.isJudgeReady(problem.getId(), publishedVersionId)) {
                throw new BusinessException(ResultCodeEnum.PROBLEM_NOT_JUDGE_READY);
            }
            submission.setProblemVersionId(publishedVersionId);
        }
        submission.setUserId(userId);
        submission.setLanguage(dto.getLanguage());
        submission.setCode(dto.getCode());
        submission.setStatus(SubmissionStatusEnum.PENDING.getCode()); // 状态设为排队中

        // 保存提交记录
        boolean saved = save(submission);
        if (!saved) {
            throw new BusinessException(ResultCodeEnum.CREATE_ERROR);
        }

        // 更新题目提交数
        if (!problemService.incrementSubmitCount(dto.getProblemId())) {
            throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
        }

        JudgeAttempt attempt = new JudgeAttempt();
        attempt.setSubmissionId(submission.getId());
        attempt.setAttemptNo(1);
        attempt.setStatus("QUEUED");
        if (judgeAttempts.insert(attempt) != 1) {
            throw new BusinessException(ResultCodeEnum.CREATE_ERROR);
        }

        // 与提交记录处于同一数据库事务；消息由独立发布器可靠投递。
        submissionOutbox.enqueue(submission);

        return submission.getId();
    }

    @Override
    public SubmissionVO getSubmissionById(Long id, Long userId) {
        // 获取提交记录
        Submission submission = getById(id);
        if (submission == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }

        // 检查权限：只有管理员或者提交者本人可以查看代码
        User user = userService.getById(userId);
        boolean isAdmin = user != null && (user.getRole() == 1 || user.getRole() == 2);
        boolean isOwner = Objects.equals(userId, submission.getUserId());

        if (!isAdmin && !isOwner) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        return convertToVO(submission);
    }

    @Override
    public IPage<SubmissionVO> getSubmissionList(SubmissionQueryDTO queryDTO, Long userId) {
        // 创建分页对象
        Page<SubmissionVO> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());

        // 检查权限：普通用户只能查看自己的提交或公开题目的提交
        User user = userService.getById(userId);
        boolean isAdmin = user != null && (user.getRole() == 1 || user.getRole() == 2);

        // 非管理员用户，只能查看自己的提交
        if (!isAdmin && queryDTO.getUserId() != null && !queryDTO.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        Long effectiveUserId = isAdmin ? queryDTO.getUserId() : userId;

        // 查询提交列表
        IPage<SubmissionVO> submissionPage = baseMapper.getSubmissionList(
                page,
                effectiveUserId,
                queryDTO.getProblemId(),
                queryDTO.getLanguage(),
                queryDTO.getStatus()
        );

        // 处理每个提交记录
        for (SubmissionVO vo : submissionPage.getRecords()) {
            // 设置状态描述
            SubmissionStatusEnum statusEnum = SubmissionStatusEnum.getByCode(vo.getStatus());
            vo.setStatusText(statusEnum != null ? statusEnum.getDesc() : "未知状态");

            // 非管理员且非提交者本人，不能查看代码和错误信息
            boolean isOwner = Objects.equals(userId, vo.getUserId());
            if (!isAdmin && !isOwner) {
                vo.setCode(null);
                vo.setErrorMessage(null);
                vo.setJudgeInfo(null);
            }
        }

        return submissionPage;
    }

    /**
     * 将提交记录转换为VO
     */
    private SubmissionVO convertToVO(Submission submission) {
        if (submission == null) {
            return null;
        }

        SubmissionVO vo = new SubmissionVO();
        BeanUtils.copyProperties(submission, vo);

        // 设置状态描述
        SubmissionStatusEnum statusEnum = SubmissionStatusEnum.getByCode(vo.getStatus());
        vo.setStatusText(statusEnum != null ? statusEnum.getDesc() : "未知状态");

        return vo;
    }

    @Override
    public SubmissionVO getUserBestSubmission(Long userId, Long problemId) {
        Submission submission = baseMapper.getUserBestSubmission(userId, problemId);
        return convertToVO(submission);
    }

    @Override
    public int countUserSubmissions(Long userId) {
        return baseMapper.countUserSubmissions(userId);
    }

    @Override
    public int countUserAcceptedProblems(Long userId) {
        return baseMapper.countUserAcceptedProblems(userId);
    }

    @Override
    @Async
    public void mockJudge(Long submissionId) {
        // ... existing code ...
    }
}
