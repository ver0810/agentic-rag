package com.agenticrag.ingestion.dto;

import java.time.LocalDateTime;
import java.util.List;

public record IngestionTaskDTO(
        String id,
        String sourceType,
        String sourceLocation,
        String sourceFileName,
        String status,
        Integer chunkCount,
        String errorMessage,
        String metadataJson,
        Integer retryCount,
        Integer maxRetries,
        LocalDateTime nextRunAt,
        String leaseOwner,
        LocalDateTime leaseUntil,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        List<IngestionTaskNodeDTO> nodes
) {
}
