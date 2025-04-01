package com.zephyr.croj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.dto.UserLoginDTO;
import com.zephyr.croj.model.dto.UserRegisterDTO;
import com.zephyr.croj.model.dto.UserUpdateDTO;
import com.zephyr.croj.model.vo.LoginResponseVO;
import com.zephyr.croj.model.vo.UserVO;
import com.zephyr.croj.service.UserService;
import com.zephyr.croj.utils.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户相关接口")
@Validated
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final HttpServletRequest request;

    @Value("${jwt.expiration}")
    private long tokenExpiration;

    @Value("${jwt.tokenPrefix}")
    private String tokenPrefix;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<Long> register(@RequestBody @Valid UserRegisterDTO registerDTO) {
        Long userId = userService.register(registerDTO);
        return Result.success("注册成功", userId);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginResponseVO> login(@RequestBody @Valid UserLoginDTO loginDTO) {
        String ip = IpUtil.getIpAddr(request);
        String token = userService.login(loginDTO, ip);

        // 构建返回结果
        LoginResponseVO loginResponse = LoginResponseVO.builder()
                .token(token)
                .tokenType(tokenPrefix)
                .expiresIn(tokenExpiration) // 24小时，应与JWT配置保持一致
                .build();

        return Result.success("登录成功", loginResponse);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    @Operation(
            summary = "获取当前登录用户信息",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<UserVO> getCurrentUser() {
        UserVO userVO = userService.getCurrentUser();
        return Result.success(userVO);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/info")
    @Operation(
            summary = "更新用户信息",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<Boolean> updateUserInfo(@RequestBody @Valid UserUpdateDTO updateDTO) {
        UserVO currentUser = userService.getCurrentUser();
        boolean result = userService.updateUserInfo(currentUser.getId(), updateDTO);
        return Result.success("更新成功", result);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    @Operation(
            summary = "修改密码",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<Boolean> updatePassword(
            @Parameter(description = "旧密码") @NotBlank(message = "旧密码不能为空") @RequestParam String oldPassword,
            @Parameter(description = "新密码") @NotBlank(message = "新密码不能为空") @RequestParam String newPassword,
            @Parameter(description = "确认密码") @NotBlank(message = "确认密码不能为空") @RequestParam String confirmPassword) {
        UserVO currentUser = userService.getCurrentUser();
        boolean result = userService.updatePassword(currentUser.getId(), oldPassword, newPassword, confirmPassword);
        return Result.success("密码修改成功", result);
    }

    /**
     * 检查用户名是否存在
     */
    @GetMapping("/check/username/{username}")
    @Operation(summary = "检查用户名是否存在")
    public Result<Boolean> checkUsername(
            @Parameter(description = "用户名") @PathVariable @NotBlank(message = "用户名不能为空") String username) {
        boolean exists = userService.checkUsernameExists(username);
        return Result.success(exists);
    }

    /**
     * 检查邮箱是否存在
     */
    @GetMapping("/check/email/{email}")
    @Operation(summary = "检查邮箱是否存在")
    public Result<Boolean> checkEmail(
            @Parameter(description = "邮箱") @PathVariable @NotBlank(message = "邮箱不能为空") String email) {
        boolean exists = userService.checkEmailExists(email);
        return Result.success(exists);
    }

    /**
     * 根据ID查询用户
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "根据ID查询用户",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<UserVO> getUserById(@PathVariable Long id) {
        UserVO userVO = userService.getUserById(id);
        return Result.success(userVO);
    }

    /**
     * 分页查询用户列表（管理员权限）
     */
    @GetMapping("/list")
    @Operation(
            summary = "分页查询用户列表",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Page<UserVO>> listUsers(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword) {
        Page<UserVO> page = userService.listUsers(current, size, keyword);
        return Result.success(page);
    }

    /**
     * 修改用户状态（管理员权限）
     */
    @PutMapping("/status/{id}/{status}")
    @Operation(
            summary = "修改用户状态",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Boolean> updateUserStatus(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "状态：0-正常，1-禁用") @PathVariable Integer status) {
        boolean result = userService.updateUserStatus(id, status);
        return Result.success("状态更新成功", result);
    }

    /**
     * 删除用户（管理员权限）
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除用户",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Boolean> removeUser(@Parameter(description = "用户ID") @PathVariable Long id) {
        boolean result = userService.removeUser(id);
        return Result.success("删除成功", result);
    }
}