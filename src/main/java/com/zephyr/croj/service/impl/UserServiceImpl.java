package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zephyr.croj.common.constants.CaptchaConstants;
import com.zephyr.croj.common.constants.EmailConstants;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.enums.UserRoleEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.mapper.UserMapper;
import com.zephyr.croj.model.dto.UserLoginDTO;
import com.zephyr.croj.model.dto.UserRegisterDTO;
import com.zephyr.croj.model.dto.UserUpdateDTO;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.UserVO;
import com.zephyr.croj.security.JwtTokenProvider;
import com.zephyr.croj.service.EmailService;
import com.zephyr.croj.service.FileService;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.utils.RedisCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private JwtTokenProvider jwtTokenProvider;

    @Resource
    private RedisCache redisCache;

    @Resource
    private EmailService emailService;

    @Resource
    private FileService fileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(UserRegisterDTO registerDTO) {
        // 验证用户名是否存在
        if (checkUsernameExists(registerDTO.getUsername())) {
            throw new BusinessException(ResultCodeEnum.ACCOUNT_EXIST);
        }

        // 验证邮箱是否存在
        if (checkEmailExists(registerDTO.getEmail())) {
            throw new BusinessException(ResultCodeEnum.EMAIL_EXIST);
        }

        // 验证两次密码是否一致
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new BusinessException(ResultCodeEnum.PASSWORD_NOT_MATCH);
        }

        // 邮箱验证码校验
        if (StringUtils.hasLength(registerDTO.getEmailCode())) {
            // 获取缓存中的邮箱验证码
            String cacheCode = redisCache.getCacheObject(EmailConstants.EMAIL_CODE_KEY + registerDTO.getEmail());
            if (cacheCode == null || !cacheCode.equals(registerDTO.getEmailCode())) {
                throw new BusinessException(ResultCodeEnum.EMAIL_CODE_ERROR);
            }
            // 验证成功，删除缓存中的验证码
            redisCache.deleteObject(EmailConstants.EMAIL_CODE_KEY + registerDTO.getEmail());
        } else {
            throw new BusinessException(ResultCodeEnum.EMAIL_CODE_ERROR);
        }

        // 常规验证码校验（图形验证码）
        verifyCaptcha(registerDTO.getCaptcha(), registerDTO.getCaptchaKey());

        // 创建用户实体
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        // 使用 Spring Security 的 BCrypt 密码编码器
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRole(0); // 默认为普通用户
        user.setStatus(0); // 默认为正常状态
        user.setIsDeleted(0); // 默认未删除
        user.setEmailVerified(1); // 通过邮箱验证码注册的，已验证

        // 保存用户
        save(user);

        return user.getId();
    }

    private void verifyCaptcha(String captcha, String captchaKey) {
        if (StringUtils.hasLength(captcha) && StringUtils.hasLength(captchaKey)) {
            String cacheCode = redisCache.getCacheObject(CaptchaConstants.CAPTCHA_CODE_KEY + ":" + captchaKey);

            // 验证码已过期或不存在
            if (cacheCode == null) {
                throw new BusinessException(ResultCodeEnum.CAPTCHA_ERROR);
            }

            // 验证码不匹配
            if (!cacheCode.equalsIgnoreCase(captcha)) {
                throw new BusinessException(ResultCodeEnum.CAPTCHA_ERROR);
            }

            // 验证通过后，删除缓存中的验证码
            redisCache.deleteObject(CaptchaConstants.CAPTCHA_CODE_KEY + ":" + captchaKey);
        } else {
            throw new BusinessException(ResultCodeEnum.CAPTCHA_ERROR);
        }
    }

    @Override
    public String login(UserLoginDTO loginDTO, String ip) {
        // 验证码校验
        verifyCaptcha(loginDTO.getCaptcha(), loginDTO.getCaptchaKey());
        // 先查询用户检查状态
        User user = baseMapper.findByUsernameOrEmail(loginDTO.getAccount());

        if (user == null) {
            // 用户不存在
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        if (user.getStatus() == 1) {
            // 用户被禁用
            throw new BusinessException(ResultCodeEnum.ACCOUNT_DISABLED);
        }

        try {
            // 使用Spring Security的AuthenticationManager进行身份验证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDTO.getAccount(), loginDTO.getPassword())
            );

            // 认证成功，设置Authentication到SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 更新最后登录时间和IP
            baseMapper.updateLastLogin(user.getId(), ip);

            // 生成JWT令牌
            List<String> roles = Collections.singletonList("ROLE_" + UserRoleEnum.getByCode(user.getRole()).getDesc());
            return jwtTokenProvider.createToken(user.getId(), user.getUsername(), roles);

        } catch (AuthenticationException e) {
            log.error("认证失败: {}", e.getMessage());
            // 统一返回账号或密码错误，不区分具体原因，避免安全问题
            throw new BusinessException(ResultCodeEnum.ACCOUNT_ERROR);
        }
    }

    @Override
    public UserVO getCurrentUser() {
        // 从Spring Security的SecurityContext中获取当前认证用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED);
        }

        // 从请求属性中获取用户ID（由JwtAuthenticationFilter设置）
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        Long userId = (Long) userIdObj;
        return getUserById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserInfo(Long userId, UserUpdateDTO updateDTO) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 如果更新邮箱，需要验证邮箱是否已存在
        if (StringUtils.hasLength(updateDTO.getEmail()) && !Objects.equals(user.getEmail(), updateDTO.getEmail())) {
            if (checkEmailExists(updateDTO.getEmail())) {
                throw new BusinessException(ResultCodeEnum.EMAIL_EXIST);
            }
            user.setEmail(updateDTO.getEmail());
            // 新邮箱需要重新验证
            user.setEmailVerified(0);
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
            throw new BusinessException(ResultCodeEnum.ACCOUNT_EXIST);
        }

        // 验证旧密码 - 使用 Spring Security 的密码匹配
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCodeEnum.PASSWORD_ERROR);
        }

        // 验证两次密码是否一致
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(ResultCodeEnum.PASSWORD_NOT_MATCH);
        }

        // 更新密码 - 使用 Spring Security 的密码编码
        user.setPassword(passwordEncoder.encode(newPassword));
        return updateById(user);
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
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
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 防止超级管理员被禁用
        if (user.getRole() == 2) {
            throw new BusinessException(ResultCodeEnum.DISABLED_ERROR);
        }
        try {
            // 更新用户状态
            user.setStatus(status);
            return updateById(user);
        } catch (Exception e) {
            throw new BusinessException(ResultCodeEnum.DISABLED_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
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
    @Transactional(rollbackFor = Exception.class)
    public boolean verifyUserEmail(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }
        user.setEmailVerified(1);
        return updateById(user);
    }

    @Override
    public boolean sendEmailVerificationCode(String email, String username) {
        if (emailService == null) {
            log.warn("邮件服务未配置，无法发送验证码");
            return false;
        }

        // 生成6位随机验证码
        String code = generateRandomCode(6);

        // 存入Redis，有效期5分钟
        redisCache.setCacheObject(
                EmailConstants.EMAIL_CODE_KEY + email,
                code,
                EmailConstants.EMAIL_CODE_EXPIRATION,
                TimeUnit.MINUTES
        );

        // 发送邮件
        return emailService.sendVerificationCode(email, username, code);
    }

    @Override
    public boolean sendVerificationLink(Long userId) {
        if (emailService == null) {
            log.warn("邮件服务未配置，无法发送验证邮件");
            return false;
        }

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.ACCOUNT_EXIST);
        }

        // 生成验证码
        String code = UUID.randomUUID().toString();

        // 存入Redis
        redisCache.setCacheObject(
                EmailConstants.EMAIL_VERIFY_CODE_KEY + userId,
                code,
                EmailConstants.EMAIL_VERIFY_EXPIRATION,
                TimeUnit.MINUTES
        );

        // 发送邮件
        return emailService.sendVerificationLink(user.getEmail(), user.getUsername(), userId, code);
    }

    /**
     * 生成随机验证码
     */
    private String generateRandomCode(int length) {
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public UserVO convertToVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        // 设置角色名称
        UserRoleEnum roleEnum = UserRoleEnum.getByCode(user.getRole());
        userVO.setRoleName(roleEnum != null ? roleEnum.getDesc() : "未知角色");

        // 设置状态名称
        String statusName = switch (user.getStatus()) {
            case 0 -> "正常";
            case 1 -> "禁用";
            default -> "未知状态";
        };
        userVO.setStatusName(statusName);

        return userVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateUserAvatar(Long userId, MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new BusinessException("头像文件不能为空");
        }

        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        try {
            // 上传新头像
            String avatarUrl = fileService.uploadAvatar(userId, avatarFile);

            // 如果用户已有头像，则删除旧头像
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                fileService.deleteAvatar(user.getAvatar());
            }

            // 更新用户头像URL
            user.setAvatar(avatarUrl);
            boolean updated = updateById(user);

            if (!updated) {
                throw new BusinessException(ResultCodeEnum.UPDATE_ERROR);
            }

            return avatarUrl;
        } catch (IOException e) {
            log.error("头像上传失败: {}", e.getMessage());
            throw new BusinessException(ResultCodeEnum.UPLOAD_ERROR);
        }
    }
}