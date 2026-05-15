package com.agenticrag.framework.infrastructure.ratelimit;

public interface RateLimiterPort {

    boolean tryAcquire(String key, int capacity, int refillPerMinute);

    boolean tryAcquire(String key);
}
