package com.agenticrag.framework.infrastructure.cache;

public interface CachePort {

    float[] getEmbedding(String text);

    void putEmbedding(String text, float[] embedding);

    Object getSearchResults(String cacheKey);

    void putSearchResults(String cacheKey, Object results);

    <T> T get(String key, Class<T> type);

    void set(String key, Object value, long ttlSeconds);

    void delete(String key);
}
