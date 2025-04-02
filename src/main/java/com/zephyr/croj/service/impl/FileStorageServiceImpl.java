package com.zephyr.croj.service.impl;

import com.zephyr.croj.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of file storage service
 */
@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${app.upload.base-dir}")
    private String uploadBaseDir;

    @Value("${app.upload.avatar.dir}")
    private String avatarDir;

    @Value("${app.upload.avatar.url-path}")
    private String avatarUrlPath;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadBaseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("Created file storage directory: {}", this.fileStorageLocation);
        } catch (IOException ex) {
            log.error("Could not create the directory where the uploaded files will be stored", ex);
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored", ex);
        }
    }

    @Override
    public String storeAvatar(Long userId, MultipartFile file) throws IOException {
        // Validate the file
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        if (originalFilename.contains("..")) {
            throw new IOException("Invalid file path: " + originalFilename);
        }

        // Extract file extension
        String fileExtension = getFileExtension(originalFilename);
        if (!isImageFile(fileExtension)) {
            throw new IOException("Only image files are allowed for avatars");
        }

        // Create date-based directory structure
        String currentDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String relativeDir = String.format("%s/%s/%s", avatarDir, currentDate, userId);
        Path targetDir = this.fileStorageLocation.resolve(relativeDir);
        Files.createDirectories(targetDir);

        // Generate unique filename
        String uniqueFilename = String.format("%s_%s%s", userId, UUID.randomUUID(), fileExtension);
        Path targetPath = targetDir.resolve(uniqueFilename);

        // Save the file
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Return the relative path for storage in database
        return String.format("%s/%s", relativeDir, uniqueFilename);
    }

    @Override
    public Path getFilePath(String relativePath) {
        return this.fileStorageLocation.resolve(relativePath).normalize();
    }

    @Override
    public String getFileUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        String contextPath = "/api"; // Either hardcode or inject from properties

        // For avatar specifically, map to the avatar URL path
        if (relativePath.startsWith(avatarDir)) {
            // Replace avatar directory with the URL path
            return contextPath + avatarUrlPath + relativePath.substring(avatarDir.length());
        }

        // For other files, just prepend the base URL path
        return "/uploads/" + relativePath;
    }

    @Override
    public void deleteFile(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return;
        }

        try {
            Path filePath = getFilePath(relativePath);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", relativePath, e);
        }
    }

    /**
     * Get file extension from original filename
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex);
    }

    /**
     * Check if the file extension is for an image
     */
    private boolean isImageFile(String fileExtension) {
        fileExtension = fileExtension.toLowerCase();
        return fileExtension.equals(".jpg") || fileExtension.equals(".jpeg") ||
                fileExtension.equals(".png") || fileExtension.equals(".gif");
    }
}