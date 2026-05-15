package com.agenticrag.framework.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisCacheAdapter implements CachePort {

    private static final String KEY_PREFIX = "rag:cache:";
    private static final String EMBEDDING_PREFIX = KEY_PREFIX + "embed:";
    private static final String RESULT_PREFIX = KEY_PREFIX + "result:";
    private static final long DEFAULT_TTL = 300;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public float[] getEmbedding(String text) {
        try {
            Object val = redisTemplate.opsForValue().get(EMBEDDING_PREFIX + sha256Hex(text));
            if (val instanceof float[] floats) {
                return floats;
            }
        } catch (Exception e) {
            log.warn("Redis getEmbedding failed", e);
        }
        return null;
    }

    @Override
    public void putEmbedding(String text, float[] embedding) {
        try {
            redisTemplate.opsForValue().set(EMBEDDING_PREFIX + sha256Hex(text), embedding, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis putEmbedding failed", e);
        }
    }

    @Override
    public Object getSearchResults(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(RESULT_PREFIX + cacheKey);
        } catch (Exception e) {
            log.warn("Redis getSearchResults failed: key={}", cacheKey, e);
            return null;
        }
    }

    @Override
    public void putSearchResults(String cacheKey, Object results) {
        try {
            redisTemplate.opsForValue().set(RESULT_PREFIX + cacheKey, results, 2, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis putSearchResults failed: key={}", cacheKey, e);
        }
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        } catch (Exception e) {
            log.warn("Redis get failed: key={}", key, e);
        }
        return null;
    }

    @Override
    public void set(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set failed: key={}", key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            redisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("Redis delete failed: key={}", key, e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
