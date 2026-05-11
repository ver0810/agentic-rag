package com.agenticrag.infra.ai.port.reranker;

import java.util.List;
import java.util.Map;

public interface RerankerPort {

    List<RerankResult> rerank(String query, List<RerankCandidate> candidates, int topK);

    record RerankCandidate(
            String chunkId,
            String content,
            float originalScore,
            Map<String, Object> metadata
    ) {}

    record RerankResult(
            String chunkId,
            String content,
            float score,
            Map<String, Object> metadata
    ) {}
}
