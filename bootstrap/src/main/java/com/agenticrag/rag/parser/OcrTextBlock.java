package com.agenticrag.rag.parser;

import java.util.Map;

public record OcrTextBlock(
        String text,
        Map<String, Float> bbox,
        double confidence,
        Map<String, Object> attributes
) {}
