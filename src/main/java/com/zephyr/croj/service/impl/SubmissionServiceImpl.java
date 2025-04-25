package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.enums.SubmissionStatusEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.SubmissionMapper;
import com.zephyr.croj.model.dto.SubmissionDTO;
import com.zephyr.croj.model.dto.SubmissionQueryDTO;
import com.zephyr.croj.model.entity.Problem;
import com.zephyr.croj.model.entity.Submission;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.SubmissionVO;
import com.zephyr.croj.service.ProblemService;
import com.zephyr.croj.service.SubmissionService;
import com.zephyr.croj.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 提交记录服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final UserService userService;
    private final ProblemService problemService;
    private final Random random = new Random();
    private final RocketMQTemplate rocketMQTemplate;

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
        problemService.incrementSubmitCount(dto.getProblemId());

        // 发送消息到 RocketMQ
        log.info("准备发送 RocketMQ 消息，submissionId: {}", submission.getId());
        try {
            rocketMQTemplate.convertAndSend("submission-topic", submission.getId());
            log.info("RocketMQ 消息发送成功，submissionId: {}", submission.getId());
        } catch (Exception e) {
            log.error("发送 RocketMQ 消息失败，submissionId: {}", submission.getId(), e);
            // 这里可以根据业务需求决定是否需要回滚事务或进行其他补偿操作
            // 例如，可以抛出异常让 @Transactional 回滚
             throw new BusinessException(ResultCodeEnum.SYSTEM_ERROR);
        }

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
        boolean isOwner = userId.equals(submission.getUserId());

        // 非管理员且非提交者本人，只能查看提交基本信息，不能查看代码
        SubmissionVO vo = convertToVO(submission);
        if (!isAdmin && !isOwner) {
            vo.setCode(null);
            vo.setErrorMessage(null);
            vo.setJudgeInfo(null);
        }

        return vo;
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

        // 查询提交列表
        IPage<SubmissionVO> submissionPage = baseMapper.getSubmissionList(
                page,
                queryDTO.getUserId(),
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
            boolean isOwner = userId.equals(vo.getUserId());
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