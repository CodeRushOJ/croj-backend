package com.zephyr.croj.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

/**
 * File storage service interface for handling file uploads
 */
public interface FileStorageService {

    /**
     * Store an avatar file
     *
     * @param userId  User ID
     * @param file    Avatar file to store
     * @return        Relative URL path to the stored avatar
     * @throws IOException if file storage fails
     */
    String storeAvatar(Long userId, MultipartFile file) throws IOException;

    /**
     * Get the absolute file system path for a file path
     *
     * @param relativePath Relative path of the file
     * @return Absolute path in the file system
     */
    Path getFilePath(String relativePath);

    /**
     * Generate a URL for accessing the uploaded file
     *
     * @param relativePath Relative path of the file
     * @return URL path that can be used to access the file
     */
    String getFileUrl(String relativePath);

    /**
     * Delete a file
     *
     * @param relativePath Relative path of the file to delete
     */
    void deleteFile(String relativePath);
}