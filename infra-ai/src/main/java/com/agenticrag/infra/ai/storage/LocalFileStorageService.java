package com.agenticrag.infra.ai.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
public class LocalFileStorageService implements FileStorageService {

    @Value("${agenticrag.storage.local.base-path:./uploads}")
    private String basePath;

    @Override
    public String store(InputStream inputStream, String path, String contentType) {
        try {
            Path filePath = Path.of(basePath, path);
            Files.createDirectories(filePath.getParent());
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {}", filePath.toAbsolutePath());
            return path;
        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + path, e);
        }
    }

    @Override
    public InputStream load(String path) {
        try {
            Path filePath = Path.of(basePath, path);
            log.info("Loading file: {}", filePath.toAbsolutePath());
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to load file: " + path + " (base-path: " + basePath + ")", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(Path.of(basePath, path));
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + path, e);
        }
    }

    @Override
    public String getUrl(String path) {
        return "/api/files/" + path;
    }
}
