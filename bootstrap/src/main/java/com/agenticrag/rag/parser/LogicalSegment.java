package com.agenticrag.rag.parser;

import java.util.Map;

public record LogicalSegment(
        String id,
        String type,
        String content,
        String headingPath,
        int startOrder,
        int endOrder,
        Map<String, Object> metadata
) {}
