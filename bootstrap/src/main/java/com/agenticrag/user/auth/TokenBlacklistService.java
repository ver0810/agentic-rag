package com.agenticrag.user.auth;

import com.agenticrag.framework.infrastructure.cache.CachePort;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "token:blacklist:";

    private final CachePort cachePort;

    public TokenBlacklistService(CachePort cachePort) {
        this.cachePort = cachePort;
    }

    public void blacklist(String token, Instant expiresAt) {
        if (!StringUtils.hasText(token) || expiresAt == null) {
            return;
        }
        long ttlSeconds = Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds());
        cachePort.set(KEY_PREFIX + token, "1", ttlSeconds);
    }

    public boolean isBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String val = cachePort.get(KEY_PREFIX + token, String.class);
        return "1".equals(val);
    }
}
