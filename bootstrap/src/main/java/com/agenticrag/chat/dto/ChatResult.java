package com.agenticrag.chat.dto;

import com.agenticrag.rag.query.RagQueryResult;

import java.util.List;

public record ChatResult(
        String answer,
        String sourceType,
        String scene,
        String kbId,
        String traceId,
        String rewrittenQuery,
        List<RagQueryResult.Citation> citations,
        List<RagQueryResult.RetrievedChunk> retrievedChunks
) {}
