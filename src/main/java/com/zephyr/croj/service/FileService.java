package com.zephyr.croj.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件服务接口
 * 处理文件上传和管理
 */
public interface FileService {

    /**
     * 上传用户头像
     *
     * @param userId 用户ID
     * @param file 头像文件
     * @return 头像访问URL
     * @throws IOException IO异常
     */
    String uploadAvatar(Long userId, MultipartFile file) throws IOException;

    /**
     * 删除用户头像
     *
     * @param avatarUrl 头像URL
     * @return 是否删除成功
     */
    boolean deleteAvatar(String avatarUrl);
}