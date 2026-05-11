package com.agenticrag.knowledge.service;

import java.util.List;
import java.util.Map;

public record ChunkResult(
        String content,
        String headingPath,
        Map<String, Object> metadata
) {
    public ChunkResult(String content, String headingPath) {
        this(content, headingPath, Map.of());
    }
}
