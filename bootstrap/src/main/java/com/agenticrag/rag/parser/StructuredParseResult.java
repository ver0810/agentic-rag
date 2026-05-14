package com.agenticrag.rag.parser;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record StructuredParseResult(
        List<LogicalSegment> segments,
        List<PageDebugInfo> pages,
        Map<String, Object> documentMetadata
) {
    public StructuredParseResult(List<LogicalSegment> segments, Map<String, Object> documentMetadata) {
        this(segments, List.of(), documentMetadata);
    }

    public String asPlainText() {
        return segments == null ? "" : segments.stream()
                .map(LogicalSegment::content)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
    }
}
