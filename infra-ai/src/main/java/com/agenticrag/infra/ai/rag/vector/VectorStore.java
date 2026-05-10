package com.agenticrag.infra.ai.rag.vector;

import java.util.List;
import java.util.Map;

public interface VectorStore {

    void store(String chunkId, String content, float[] embedding, Map<String, Object> metadata);

    List<VectorSearchResult> search(float[] queryEmbedding, int topK);

    List<VectorSearchResult> search(float[] queryEmbedding, int topK, Map<String, Object> filter);

    void deleteByDocId(String docId);

    void deleteByKbId(String kbId);

    record VectorSearchResult(String chunkId, String content, float score, Map<String, Object> metadata) {}
}
