package com.agenticrag.ragtrace.dto;

import java.time.LocalDateTime;

public record RagTraceNodeDTO(
        String nodeId,
        String nodeType,
        String nodeName,
        String status,
        String errorMessage,
        Long durationMs,
        String extraData,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
