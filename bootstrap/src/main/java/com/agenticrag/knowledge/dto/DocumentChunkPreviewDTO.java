package com.agenticrag.knowledge.dto;

import java.util.Map;

public record DocumentChunkPreviewDTO(
        int chunkIndex,
        String content,
        String headingPath,
        Map<String, Object> metadata
) {
}
