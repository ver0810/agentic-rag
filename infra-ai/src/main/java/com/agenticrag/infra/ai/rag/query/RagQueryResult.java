package com.agenticrag.infra.ai.rag.query;

import java.util.List;

public record RagQueryResult(
        String answer,
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
