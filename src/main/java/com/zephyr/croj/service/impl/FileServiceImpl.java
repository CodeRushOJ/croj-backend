package com.zephyr.croj.service.impl;

import com.zephyr.croj.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件服务实现类
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.avatar.dir:avatar}")
    private String avatarDir;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) throws IOException {
        // 验证文件
        validateImageFile(file);

        // 创建上传目录
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String relativePath = String.format("%s/%s/%s", avatarDir, today, userId);
        Path uploadPath = Paths.get(uploadDir, relativePath);
        Files.createDirectories(uploadPath);

        // 生成新的文件名 (userId_uuid.extension)
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String newFilename = userId + "_" + UUID.randomUUID().toString() + "." + extension;

        // 保存文件
        Path filePath = uploadPath.resolve(newFilename);
        file.transferTo(filePath.toFile());

        // 返回可访问的URL (使用相对路径，通过资源处理器访问)
        return String.format("%s/uploads/%s/%s", contextPath, relativePath, newFilename);
    }

    @Override
    public boolean deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return false;
        }

        try {
            // 解析URL获取文件路径
            // 从URL中提取文件路径，格式为: /api/uploads/avatar/日期/用户ID/文件名
            String filePathStr = avatarUrl.substring(avatarUrl.indexOf("/uploads/") + 9);
            Path filePath = Paths.get(uploadDir, filePathStr);

            // 检查文件是否存在并删除
            File file = filePath.toFile();
            if (file.exists() && file.isFile()) {
                return file.delete();
            }
            return false;
        } catch (Exception e) {
            log.error("删除头像文件失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证是否为图片文件
     *
     * @param file 上传的文件
     * @throws IOException IO异常
     */
    private void validateImageFile(MultipartFile file) throws IOException {
        // 检查文件是否为空
        if (file == null || file.isEmpty()) {
            throw new IOException("上传的文件为空");
        }

        // 检查文件大小 (最大2MB)
        long maxSize = 2 * 1024 * 1024; // 2MB
        if (file.getSize() > maxSize) {
            throw new IOException("文件大小超过限制 (最大2MB)");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("只允许上传图片文件");
        }

        // 允许的图片格式
        String[] allowedExtensions = {"jpg", "jpeg", "png", "gif"};
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IOException("文件名不能为空");
        }

        String extension = StringUtils.getFilenameExtension(originalFilename);

        if (extension == null) {
            throw new IOException("无法识别文件扩展名");
        }

        boolean isAllowed = false;
        for (String allowedExtension : allowedExtensions) {
            if (extension.equalsIgnoreCase(allowedExtension)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            throw new IOException("只允许上传 JPG, JPEG, PNG, GIF 格式的图片");
        }
    }

    /**
     * 获取应用的绝对路径
     *
     * @return 应用的绝对路径
     */
    private String getApplicationPath() {
        return System.getProperty("user.dir");
    }

    /**
     * 确保目录存在，如果不存在则创建
     *
     * @param directory 目录路径
     * @throws IOException IO异常
     */
    private void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        } else if (!Files.isDirectory(directory)) {
            throw new IOException("路径存在但不是目录: " + directory);
        }
    }
}