package com.agenticrag.rag.query;

import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RagCacheService {

    private final Cache<String, float[]> embeddingCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    private final Cache<String, List<? extends VectorIndexPort.SearchResult>> resultCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    public float[] getEmbedding(String text) {
        return embeddingCache.getIfPresent(text);
    }

    public void putEmbedding(String text, float[] embedding) {
        embeddingCache.put(text, embedding);
    }

    public List<? extends VectorIndexPort.SearchResult> getResults(String cacheKey) {
        return resultCache.getIfPresent(cacheKey);
    }

    public void putResults(String cacheKey, List<? extends VectorIndexPort.SearchResult> results) {
        resultCache.put(cacheKey, results);
    }

    public static String buildResultCacheKey(String query, String kbId, int topK) {
        return kbId + ":" + topK + ":" + query.hashCode();
    }
}
