package com.agenticrag.knowledge.dto;

import com.agenticrag.rag.parser.LogicalSegment;
import com.agenticrag.rag.parser.PageDebugInfo;

import java.util.List;
import java.util.Map;

public record DocumentStructurePreviewDTO(
        String docId,
        String docName,
        String fileType,
        String parseStrategy,
        String chunkStrategy,
        Map<String, Object> documentMetadata,
        List<PageDebugInfo> pages,
        List<LogicalSegment> segments,
        List<DocumentChunkPreviewDTO> chunks
) {
}
