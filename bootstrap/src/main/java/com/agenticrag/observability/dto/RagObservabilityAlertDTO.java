package com.agenticrag.observability.dto;

import java.util.Map;

public record RagObservabilityAlertDTO(
        String code,
        String level,
        String status,
        String message,
        Double currentValue,
        Double baselineValue,
        Double thresholdValue,
        Map<String, Object> details
) {
}
