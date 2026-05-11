package com.agenticrag.knowledge.dto;

public record IngestionTaskNodeDTO(
        String nodeId,
        String nodeType,
        Integer nodeOrder,
        String status,
        Long durationMs,
        String message,
        String errorMessage,
        String outputJson
) {
}
