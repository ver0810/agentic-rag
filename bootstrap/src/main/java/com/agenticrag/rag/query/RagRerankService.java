package com.agenticrag.rag.query;

import com.agenticrag.infra.ai.port.reranker.RerankerPort;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RagRerankService {

    private final RerankerPort rerankerPort;

    public RagRerankService(RerankerPort rerankerPort) {
        this.rerankerPort = rerankerPort;
    }

    public List<RankedSearchResult> rerank(String query,
                                           List<? extends VectorIndexPort.SearchResult> results,
                                           int topK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<RerankerPort.RerankCandidate> candidates = results.stream()
                .map(r -> new RerankerPort.RerankCandidate(r.chunkId(), r.content(), r.score(), r.metadata()))
                .toList();

        return rerankerPort.rerank(query, candidates, topK).stream()
                .map(r -> new RankedSearchResult(r.chunkId(), r.content(), r.score(), r.metadata()))
                .toList();
    }

    public record RankedSearchResult(String chunkId, String content, float score, Map<String, Object> metadata)
            implements VectorIndexPort.SearchResult {}
}
