package com.agenticrag.observability.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RagAlertDispatchResultDTO(
        boolean notificationsEnabled,
        boolean dispatched,
        int activeAlertCount,
        LocalDateTime dispatchedAt,
        String destination,
        List<RagObservabilityAlertDTO> alerts
) {
}
