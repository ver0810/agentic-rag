package com.agenticrag.framework.infrastructure.mq;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MqEvent(
        String eventId,
        String eventType,
        Map<String, String> payload,
        Instant timestamp) {

    public static MqEvent of(String eventType, Map<String, String> payload) {
        return new MqEvent(
                UUID.randomUUID().toString().substring(0, 12),
                eventType,
                payload,
                Instant.now());
    }
}
