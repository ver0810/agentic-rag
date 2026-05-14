package com.agenticrag.rag.parser;

import java.util.Map;

public record LayoutBlock(
        String id,
        String type,
        Map<String, Float> bbox,
        String text,
        String html,
        int pageNum,
        int columnIndex,
        double confidence,
        Map<String, Object> attributes
) {}
