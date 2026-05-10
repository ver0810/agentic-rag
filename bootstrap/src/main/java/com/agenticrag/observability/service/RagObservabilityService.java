package com.agenticrag.observability.service;

import com.agenticrag.observability.dto.RagObservabilityAlertDTO;
import com.agenticrag.observability.dto.RagAlertDispatchResultDTO;
import com.agenticrag.observability.dto.RagObservabilityMetricsDTO;
import com.agenticrag.observability.dto.RagObservabilitySummaryDTO;

import java.util.List;

public interface RagObservabilityService {

    RagObservabilityMetricsDTO getMetrics(String userId, int hours);

    List<RagObservabilityAlertDTO> getAlerts(String userId, int hours, int baselineHours);

    RagObservabilitySummaryDTO getSummary(String userId, int hours, int baselineHours);

    RagAlertDispatchResultDTO dispatchAlerts(String userId, int hours, int baselineHours, boolean forceDispatch);

    List<RagAlertDispatchResultDTO> dispatchAlertsForActiveUsers(int hours, int baselineHours, boolean forceDispatch);
}
