package com.zephyr.croj.controller;

import com.zephyr.croj.common.response.Result;
import com.zephyr.croj.model.vo.UserVO;
import com.zephyr.croj.service.FileStorageService;
import com.zephyr.croj.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户头像上传控制器
 */
@RestController
@RequestMapping("/user/avatar")
@Tag(name = "用户头像", description = "用户头像相关接口")
@Slf4j
@RequiredArgsConstructor
public class AvatarController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    @Value("${app.upload.avatar.dir}")
    private String avatarDir;

    @Value("${app.upload.avatar.url-path}")
    private String avatarUrlPath;

    @Value("${app.upload.max-file-size:2MB}")
    private String maxFileSizeStr;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * 上传头像
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "上传头像",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        // 获取当前用户
        UserVO currentUser = userService.getCurrentUser();

        try {
            // 解析最大文件大小配置（将字符串如"2MB"转换为字节数）
            long maxFileSize = DataSize.parse(maxFileSizeStr).toBytes();

            // 验证文件大小
            if (file.getSize() > maxFileSize) {
                return Result.error("文件大小超过限制，最大支持" + maxFileSizeStr);
            }

            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("文件类型不支持，只允许上传图片");
            }

            // 存储头像文件
            String relativePath = fileStorageService.storeAvatar(currentUser.getId(), file);

            // 删除旧头像（如果存在）
            String oldAvatar = currentUser.getAvatar();
            if (oldAvatar != null && !oldAvatar.isEmpty()) {
                // 尝试提取相对路径
                String oldPath = extractRelativePathFromUrl(oldAvatar);
                if (oldPath != null) {
                    fileStorageService.deleteFile(oldPath);
                }
            }

            // 生成访问头像的URL
            String avatarUrl = fileStorageService.getFileUrl(relativePath);

            // 更新数据库中的用户头像
            userService.updateUserAvatar(currentUser.getId(), avatarUrl);

            return Result.success("头像上传成功", avatarUrl);
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return Result.error("头像上传失败: " + e.getMessage());
        }
    }

    /**
     * 从头像URL提取相对路径
     * 这是一个辅助方法，用于从URL转换为文件路径
     */
    private String extractRelativePathFromUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return null;
        }

        // 移除上下文路径前缀（如果存在）
        String urlWithoutContext = avatarUrl;
        if (StringUtils.hasLength(contextPath) && avatarUrl.startsWith(contextPath)) {
            urlWithoutContext = avatarUrl.substring(contextPath.length());
        }

        // 使用配置的URL路径匹配
        if (urlWithoutContext.startsWith(avatarUrlPath)) {
            return avatarDir + urlWithoutContext.substring(avatarUrlPath.length());
        }
        return null;
    }
}