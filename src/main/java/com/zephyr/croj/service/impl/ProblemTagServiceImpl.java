package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.enums.UserRoleEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.ProblemTagMapper;
import com.zephyr.croj.mapper.ProblemTagRelationMapper;
import com.zephyr.croj.model.entity.ProblemTag;
import com.zephyr.croj.model.entity.ProblemTagRelation;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.ProblemTagVO;
import com.zephyr.croj.service.ProblemTagService;
import com.zephyr.croj.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 问题标签服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemTagServiceImpl extends ServiceImpl<ProblemTagMapper, ProblemTag> implements ProblemTagService {

    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTag(ProblemTag tag, Long userId) {
        // 检查用户是否有权限创建标签
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 只有管理员和超级管理员可以创建标签
        if (!UserRoleEnum.ADMIN.getCode().equals(user.getRole())
                && !UserRoleEnum.SUPER_ADMIN.getCode().equals(user.getRole())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 检查标签名称是否已存在
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getName, tag.getName());
        wrapper.eq(ProblemTag::getIsDeleted, 0);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "标签名称已存在");
        }

        // 保存标签
        save(tag);

        return tag.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTag(ProblemTag tag, Long userId) {
        // 检查用户是否有权限更新标签
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 只有管理员和超级管理员可以更新标签
        if (!UserRoleEnum.ADMIN.getCode().equals(user.getRole())
                && !UserRoleEnum.SUPER_ADMIN.getCode().equals(user.getRole())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 检查标签是否存在
        ProblemTag existTag = getById(tag.getId());
        if (existTag == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "标签不存在");
        }

        // 如果修改了标签名称，检查名称是否已存在
        if (!existTag.getName().equals(tag.getName())) {
            LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProblemTag::getName, tag.getName());
            wrapper.eq(ProblemTag::getIsDeleted, 0);
            wrapper.ne(ProblemTag::getId, tag.getId());
            if (count(wrapper) > 0) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "标签名称已存在");
            }
        }

        return updateById(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTag(Long id, Long userId) {
        // 检查用户是否有权限删除标签
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 只有管理员和超级管理员可以删除标签
        if (!UserRoleEnum.ADMIN.getCode().equals(user.getRole())
                && !UserRoleEnum.SUPER_ADMIN.getCode().equals(user.getRole())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }

        // 检查标签是否存在
        ProblemTag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "标签不存在");
        }

        // 检查标签是否被使用
        int count = problemTagRelationMapper.countProblemsByTagId(id);
        if (count > 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "该标签已被使用，无法删除");
        }

        return removeById(id);
    }

    @Override
    public List<ProblemTagVO> getAllTags() {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getIsDeleted, 0);
        wrapper.orderByAsc(ProblemTag::getId);

        List<ProblemTag> tags = list(wrapper);
        return tags.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public IPage<ProblemTagVO> getTagList(long current, long size, String keyword) {
        Page<ProblemTag> page = new Page<>(current, size);
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getIsDeleted, 0);

        if (StringUtils.hasLength(keyword)) {
            wrapper.like(ProblemTag::getName, keyword);
        }

        wrapper.orderByAsc(ProblemTag::getId);

        IPage<ProblemTag> tagPage = page(page, wrapper);

        // 转换为VO
        List<ProblemTagVO> records = tagPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        IPage<ProblemTagVO> result = new Page<>(tagPage.getCurrent(), tagPage.getSize(), tagPage.getTotal());
        result.setRecords(records);

        return result;
    }

    @Override
    public ProblemTagVO getTagById(Long id) {
        ProblemTag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "标签不存在");
        }

        return convertToVO(tag);
    }

    @Override
    public List<ProblemTagVO> getTagsByProblemId(Long problemId) {
        List<ProblemTag> tags = baseMapper.getTagsByProblemId(problemId);
        return tags.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProblemTagVO> getTagsByProblemIds(List<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return List.of();
        }

        List<ProblemTag> tags = baseMapper.getTagsByProblemIds(problemIds);
        return tags.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveProblemTags(Long problemId, List<Long> tagIds) {
        if (problemId == null || tagIds == null || tagIds.isEmpty()) {
            return false;
        }

        // 删除现有关联
        deleteProblemTags(problemId);

        // 批量插入新关联
        try {
            problemTagRelationMapper.batchInsert(problemId, tagIds);
            return true;
        } catch (Exception e) {
            log.error("保存题目标签关联失败", e);
            throw new BusinessException(ResultCodeEnum.ERROR.getCode(), "保存题目标签关联失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProblemTags(Long problemId) {
        if (problemId == null) {
            return false;
        }

        try {
            problemTagRelationMapper.deleteByProblemId(problemId);
            return true;
        } catch (Exception e) {
            log.error("删除题目标签关联失败", e);
            throw new BusinessException(ResultCodeEnum.ERROR.getCode(), "删除题目标签关联失败");
        }
    }

    /**
     * 将标签实体转换为VO
     *
     * @param tag 标签实体
     * @return 标签VO
     */
    private ProblemTagVO convertToVO(ProblemTag tag) {
        ProblemTagVO vo = new ProblemTagVO();
        BeanUtils.copyProperties(tag, vo);
        return vo;
    }
}