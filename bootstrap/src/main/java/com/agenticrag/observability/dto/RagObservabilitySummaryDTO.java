package com.agenticrag.observability.dto;

import java.util.List;

public record RagObservabilitySummaryDTO(
        RagObservabilityMetricsDTO metrics,
        List<RagObservabilityAlertDTO> alerts
) {
}
