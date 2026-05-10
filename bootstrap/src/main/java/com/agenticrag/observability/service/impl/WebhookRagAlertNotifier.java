package com.agenticrag.observability.service.impl;

import com.agenticrag.infra.ai.config.AiObservabilityProperties;
import com.agenticrag.observability.dto.RagAlertDispatchResultDTO;
import com.agenticrag.observability.dto.RagObservabilityAlertDTO;
import com.agenticrag.observability.dto.RagObservabilityMetricsDTO;
import com.agenticrag.observability.service.RagAlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WebhookRagAlertNotifier implements RagAlertNotifier {

    private final AiObservabilityProperties observabilityProperties;
    private final RestClient restClient;
    private final Map<String, String> lastDispatchSignature = new ConcurrentHashMap<>();

    public WebhookRagAlertNotifier(AiObservabilityProperties observabilityProperties) {
        this.observabilityProperties = observabilityProperties;
        this.restClient = RestClient.create();
    }

    @Override
    public RagAlertDispatchResultDTO notify(String userId,
                                            RagObservabilityMetricsDTO metrics,
                                            List<RagObservabilityAlertDTO> alerts,
                                            boolean forceDispatch) {
        List<RagObservabilityAlertDTO> activeAlerts = alerts.stream()
                .filter(alert -> "ACTIVE".equals(alert.status()))
                .toList();
        boolean notificationsEnabled = observabilityProperties.getAlerts().isNotificationsEnabled();
        String destination = observabilityProperties.getAlerts().getWebhookUrl();
        if (!notificationsEnabled || !StringUtils.hasText(destination)) {
            return new RagAlertDispatchResultDTO(
                    notificationsEnabled,
                    false,
                    activeAlerts.size(),
                    null,
                    destination,
                    activeAlerts);
        }
        if (activeAlerts.isEmpty()) {
            return new RagAlertDispatchResultDTO(
                    notificationsEnabled,
                    false,
                    0,
                    null,
                    destination,
                    activeAlerts);
        }

        String signature = buildSignature(activeAlerts);
        if (!forceDispatch && signature.equals(lastDispatchSignature.get(userId))) {
            return new RagAlertDispatchResultDTO(
                    notificationsEnabled,
                    false,
                    activeAlerts.size(),
                    null,
                    destination,
                    activeAlerts);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("dispatchedAt", LocalDateTime.now().toString());
        payload.put("metrics", metrics);
        payload.put("alerts", activeAlerts);

        try {
            restClient.post()
                    .uri(destination)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            lastDispatchSignature.put(userId, signature);
            log.info("Dispatched {} RAG alerts for user {}", activeAlerts.size(), userId);
            return new RagAlertDispatchResultDTO(
                    true,
                    true,
                    activeAlerts.size(),
                    LocalDateTime.now(),
                    destination,
                    activeAlerts);
        } catch (Exception ex) {
            log.warn("Failed to dispatch RAG alerts for user {}: {}", userId, ex.getMessage());
            return new RagAlertDispatchResultDTO(
                    true,
                    false,
                    activeAlerts.size(),
                    null,
                    destination,
                    activeAlerts);
        }
    }

    private String buildSignature(List<RagObservabilityAlertDTO> alerts) {
        return alerts.stream()
                .map(alert -> alert.code() + ":" + alert.status() + ":" + alert.currentValue())
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
    }
}
