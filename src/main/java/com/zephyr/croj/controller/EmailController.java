package com.zephyr.croj.controller;

import com.zephyr.croj.common.constants.EmailConstants;
import com.zephyr.croj.common.enums.ResultCodeEnum;
import com.zephyr.croj.common.exception.BusinessException;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.vo.UserVO;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.utils.RedisCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 邮箱相关控制器
 */
@RestController
@RequestMapping("/email")
@Tag(name = "邮箱管理", description = "邮箱验证相关接口")
@Slf4j
@RequiredArgsConstructor
public class EmailController {

    private final UserService userService;
    private final RedisCache redisCache;

    /**
     * 发送邮箱验证码（用于注册）
     */
    @PostMapping("/code")
    @Operation(summary = "发送邮箱验证码")
    public Result<String> sendVerificationCode(
            @Parameter(description = "邮箱") @RequestParam String email,
            @Parameter(description = "用户名") @RequestParam String username
    ) {
        // 检查邮箱是否已存在
        if (userService.checkEmailExists(email)) {
            throw new BusinessException(ResultCodeEnum.EMAIL_EXIST);
        }

        // 发送验证码
        boolean success = userService.sendEmailVerificationCode(email, username);

        if (success) {
            return Result.success("验证码发送成功");
        } else {
            return Result.error("验证码发送失败");
        }
    }

    /**
     * 发送邮箱验证链接（用于已注册用户）
     */
    @PostMapping("/send-verification")
    @Operation(
            summary = "发送邮箱验证链接",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<String> sendVerificationLink() {
        // 获取当前用户
        UserVO user = userService.getCurrentUser();

        // 如果邮箱已验证，则返回错误
        if (user.getEmailVerified() != null && user.getEmailVerified() == 1) {
            throw new BusinessException(ResultCodeEnum.EMAIL_VERIFIED);
        }

        // 发送验证链接邮件
        boolean success = userService.sendVerificationLink(user.getId());

        if (success) {
            return Result.success("验证邮件发送成功");
        } else {
            return Result.error("验证邮件发送失败");
        }
    }

    /**
     * 验证邮箱
     */
    @GetMapping("/verify")
    @Operation(
            summary = "验证邮箱",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<String> verifyEmail(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "验证码") @RequestParam String code
    ) {
        // 检查验证码是否存在
        String cacheCode = redisCache.getCacheObject(EmailConstants.EMAIL_VERIFY_CODE_KEY + userId);

        if (!StringUtils.hasText(cacheCode) || !cacheCode.equals(code)) {
            throw new BusinessException(ResultCodeEnum.EMAIL_CODE_ERROR);
        }

        // 更新用户邮箱验证状态
        boolean success = userService.verifyUserEmail(userId);

        if (success) {
            // 删除验证码
            redisCache.deleteObject(EmailConstants.EMAIL_VERIFY_CODE_KEY + userId);
            return Result.success("邮箱验证成功");
        } else {
            return Result.error("邮箱验证失败");
        }
    }
}