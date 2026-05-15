package com.agenticrag.framework.infrastructure.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisRateLimiterAdapter implements RateLimiterPort {

    private static final String PREFIX = "rag:ratelimit:";

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local window = 60000
            
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local last = tonumber(data[2])
            
            if tokens == nil then
                tokens = capacity
                last = now
            end
            
            local elapsed = now - last
            local new_tokens = math.floor(elapsed / window * refill)
            if new_tokens > 0 then
                tokens = math.min(capacity, tokens + new_tokens)
                last = now
            end
            
            if tokens > 0 then
                tokens = tokens - 1
                redis.call('HMSET', key, 'tokens', tokens, 'ts', last)
                redis.call('EXPIRE', key, 120)
                return '1'
            end
            
            redis.call('HMSET', key, 'tokens', tokens, 'ts', last)
            redis.call('EXPIRE', key, 120)
            return '0'
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final boolean enabled;
    private final DefaultRedisScript<String> redisScript;

    public RedisRateLimiterAdapter(StringRedisTemplate stringRedisTemplate,
                                    org.springframework.core.env.Environment env) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.enabled = Boolean.parseBoolean(env.getProperty("agenticrag.rate-limit.enabled", "true"));
        this.redisScript = new DefaultRedisScript<>(LUA_SCRIPT, String.class);
    }

    @Override
    public boolean tryAcquire(String key, int capacity, int refillPerMinute) {
        if (!enabled) {
            return true;
        }
        try {
            String result = stringRedisTemplate.execute(
                    redisScript,
                    java.util.Collections.singletonList(PREFIX + key),
                    String.valueOf(capacity),
                    String.valueOf(refillPerMinute),
                    String.valueOf(System.currentTimeMillis()));
            return !"0".equals(result);
        } catch (Exception e) {
            log.warn("Rate limiter failed (allowing request): key={}", key, e);
            return true;
        }
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 60, 30);
    }
}
