package com.agenticrag.ragtrace.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RagTraceRunDTO(
        String traceId,
        String traceName,
        String entryMethod,
        String conversationId,
        String userId,
        String status,
        String errorMessage,
        Long durationMs,
        String extraData,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<RagTraceNodeDTO> nodes
) {
}
