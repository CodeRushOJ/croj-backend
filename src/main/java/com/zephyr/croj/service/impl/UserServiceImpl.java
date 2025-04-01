package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.UserMapper;
import com.zephyr.croj.model.dto.UserLoginDTO;
import com.zephyr.croj.model.dto.UserRegisterDTO;
import com.zephyr.croj.model.dto.UserUpdateDTO;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.UserVO;
import com.zephyr.croj.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private HttpServletRequest request;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(UserRegisterDTO registerDTO) {
        // 验证用户名是否存在
        if (checkUsernameExists(registerDTO.getUsername())) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 验证邮箱是否存在
        if (checkEmailExists(registerDTO.getEmail())) {
            throw new BusinessException(400, "邮箱已存在");
        }

        // 验证两次密码是否一致
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new BusinessException(400, "两次密码不一致");
        }

        // TODO 验证码校验逻辑（实际应用中需要实现）
        // checkCaptcha(registerDTO.getCaptcha());

        // 创建用户实体
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        // 使用 Spring Security 的 BCrypt 密码编码器
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRole(0); // 默认为普通用户
        user.setStatus(0); // 默认为正常状态
        user.setIsDeleted(0); // 默认未删除

        // 保存用户
        save(user);
        return user.getId();
    }

    @Override
    public String login(UserLoginDTO loginDTO, String ip) {
        // 根据用户名或邮箱查询用户
        User user = baseMapper.findByUsernameOrEmail(loginDTO.getAccount());
        if (user == null) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        // 验证密码 - 使用 Spring Security 的密码匹配
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        // 验证用户状态
        if (user.getStatus() == 1) {
            throw new BusinessException(400, "账号已被禁用");
        }

        // 更新最后登录时间和IP
        baseMapper.updateLastLogin(user.getId(), ip);

        // 简化处理，返回用户ID作为临时令牌
        // 实际项目应使用JWT或其他token机制
        return String.valueOf(user.getId());
    }

    @Override
    public UserVO getCurrentUser() {
        // 从请求中获取用户ID
        // 这里简化处理，假设请求头中有user-id
        String userId = request.getHeader("user-id");
        if (!StringUtils.hasLength(userId)) {
            throw new BusinessException(401, "未登录");
        }

        return getUserById(Long.valueOf(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserInfo(Long userId, UserUpdateDTO updateDTO) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 如果更新邮箱，需要验证邮箱是否已存在
        if (StringUtils.hasLength(updateDTO.getEmail()) && !Objects.equals(user.getEmail(), updateDTO.getEmail())) {
            if (checkEmailExists(updateDTO.getEmail())) {
                throw new BusinessException(400, "邮箱已存在");
            }
            user.setEmail(updateDTO.getEmail());
        }

        // 更新其他字段
        if (StringUtils.hasLength(updateDTO.getAvatar())) {
            user.setAvatar(updateDTO.getAvatar());
        }
        if (updateDTO.getBio() != null) {
            user.setBio(updateDTO.getBio());
        }
        if (updateDTO.getGithub() != null) {
            user.setGithub(updateDTO.getGithub());
        }
        if (updateDTO.getSchool() != null) {
            user.setSchool(updateDTO.getSchool());
        }

        return updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePassword(Long userId, String oldPassword, String newPassword, String confirmPassword) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 验证旧密码 - 使用 Spring Security 的密码匹配
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "旧密码错误");
        }

        // 验证两次密码是否一致
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(400, "两次密码不一致");
        }

        // 更新密码 - 使用 Spring Security 的密码编码
        user.setPassword(passwordEncoder.encode(newPassword));
        return updateById(user);
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return convertToVO(user);
    }

    @Override
    public Page<UserVO> listUsers(long current, long size, String keyword) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery();

        // 添加关键字搜索条件
        if (StringUtils.hasLength(keyword)) {
            queryWrapper.like(User::getUsername, keyword)
                    .or()
                    .like(User::getEmail, keyword);
        }

        // 按创建时间降序排序
        queryWrapper.orderByDesc(User::getCreateTime);

        page(page, queryWrapper);

        // 转换为VO
        List<UserVO> records = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<UserVO> voPage = new Page<>();
        voPage.setCurrent(page.getCurrent());
        voPage.setSize(page.getSize());
        voPage.setTotal(page.getTotal());
        voPage.setPages(page.getPages());
        voPage.setRecords(records);

        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserStatus(Long userId, Integer status) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 防止超级管理员被禁用
        if (user.getRole() == 2) {
            throw new BusinessException(403, "超级管理员不能被禁用");
        }

        user.setStatus(status);
        return updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 防止超级管理员被删除
        if (user.getRole() == 2) {
            throw new BusinessException(403, "超级管理员不能被删除");
        }

        // 逻辑删除（因为使用了@TableLogic，调用removeById会执行逻辑删除）
        return removeById(userId);
    }

    @Override
    public boolean checkUsernameExists(String username) {
        return baseMapper.existsByUsername(username);
    }

    @Override
    public boolean checkEmailExists(String email) {
        return baseMapper.existsByEmail(email);
    }

    @Override
    public UserVO convertToVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        // 设置角色名称
        String roleName = switch (user.getRole()) {
            case 0 -> "普通用户";
            case 1 -> "管理员";
            case 2 -> "超级管理员";
            default -> "未知角色";
        };
        userVO.setRoleName(roleName);

        // 设置状态名称
        String statusName = switch (user.getStatus()) {
            case 0 -> "正常";
            case 1 -> "禁用";
            default -> "未知状态";
        };
        userVO.setStatusName(statusName);

        return userVO;
    }
}