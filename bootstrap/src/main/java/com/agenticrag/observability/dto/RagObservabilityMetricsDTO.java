package com.agenticrag.observability.dto;

import java.time.LocalDateTime;

public record RagObservabilityMetricsDTO(
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        long totalQueries,
        long successfulQueries,
        long failedQueries,
        double emptyRetrievalRate,
        double refusalRate,
        double averageResponseTimeMs,
        double modelErrorRate,
        long totalIngestionTasks,
        long terminalIngestionTasks,
        double documentProcessingSuccessRate,
        double ingestionRetryRate,
        double averageIngestionDurationMs,
        long estimatedChatInputTokens,
        long estimatedChatOutputTokens,
        long estimatedQueryEmbeddingTokens,
        long estimatedIngestionEmbeddingTokens,
        long estimatedTotalTokens,
        double estimatedChatCost,
        double estimatedEmbeddingCost,
        double estimatedTotalCost
) {
}
