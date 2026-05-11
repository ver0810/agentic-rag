package com.agenticrag.infra.ai.port.vector;

import java.util.List;
import java.util.Map;

public interface VectorIndexPort {

    void store(String chunkId, String content, float[] embedding, Map<String, Object> metadata);

    List<? extends SearchResult> search(float[] queryEmbedding, int topK);

    List<? extends SearchResult> search(float[] queryEmbedding, int topK, Map<String, Object> filter);

    List<? extends SearchResult> keywordSearch(String query, int topK, Map<String, Object> filter);

    void deleteByDocId(String docId);

    void deleteByKbId(String kbId);

    interface SearchResult {
        String chunkId();

        String content();

        float score();

        Map<String, Object> metadata();
    }
}
