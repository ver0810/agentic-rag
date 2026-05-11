package com.agenticrag.rag.query;

import java.util.List;

public record RagQueryResult(
        String answer,
        String traceId,
        String rewrittenQuery,
        List<Citation> citations,
        List<RetrievedChunk> retrievedChunks
) {
    public record Citation(
            String chunkId,
            String docId,
            String docName,
            Integer chunkIndex,
            float score,
            String snippet
    ) {}

    public record RetrievedChunk(
            String chunkId,
            String docId,
            String docName,
            Integer chunkIndex,
            float score,
            String content
    ) {}
}
