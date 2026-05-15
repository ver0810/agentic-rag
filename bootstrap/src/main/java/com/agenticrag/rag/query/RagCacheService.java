package com.agenticrag.rag.query;

import com.agenticrag.framework.infrastructure.cache.CachePort;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RagCacheService {

    private final CachePort cachePort;

    private final Cache<String, float[]> embeddingCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    private final Cache<String, List<? extends VectorIndexPort.SearchResult>> resultCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    public RagCacheService(CachePort cachePort) {
        this.cachePort = cachePort;
    }

    public float[] getEmbedding(String text) {
        float[] cached = embeddingCache.getIfPresent(text);
        if (cached != null) {
            return cached;
        }
        float[] redis = cachePort.getEmbedding(text);
        if (redis != null) {
            embeddingCache.put(text, redis);
        }
        return redis;
    }

    public void putEmbedding(String text, float[] embedding) {
        embeddingCache.put(text, embedding);
        cachePort.putEmbedding(text, embedding);
    }

    @SuppressWarnings("unchecked")
    public List<? extends VectorIndexPort.SearchResult> getResults(String cacheKey) {
        List<? extends VectorIndexPort.SearchResult> cached = resultCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        Object redis = cachePort.getSearchResults(cacheKey);
        if (redis instanceof List<?> list && !list.isEmpty()) {
            resultCache.put(cacheKey, (List<VectorIndexPort.SearchResult>) (List<?>) list);
            return (List<VectorIndexPort.SearchResult>) (List<?>) list;
        }
        return null;
    }

    public void putResults(String cacheKey, List<? extends VectorIndexPort.SearchResult> results) {
        resultCache.put(cacheKey, results);
        cachePort.putSearchResults(cacheKey, results);
    }

    public static String buildResultCacheKey(String query, String kbId, int topK) {
        return kbId + ":" + topK + ":" + query.hashCode();
    }
}
