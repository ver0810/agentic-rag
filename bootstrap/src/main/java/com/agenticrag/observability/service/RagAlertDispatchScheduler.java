package com.agenticrag.observability.service;

import com.agenticrag.infra.ai.config.AiObservabilityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagAlertDispatchScheduler {

    private final RagObservabilityService ragObservabilityService;
    private final AiObservabilityProperties observabilityProperties;

    public RagAlertDispatchScheduler(RagObservabilityService ragObservabilityService,
                                     AiObservabilityProperties observabilityProperties) {
        this.ragObservabilityService = ragObservabilityService;
        this.observabilityProperties = observabilityProperties;
    }

    @Scheduled(fixedDelayString = "${agenticrag.ai.observability.alerts.dispatch-interval-ms:300000}")
    public void dispatch() {
        if (!observabilityProperties.getAlerts().isNotificationsEnabled()) {
            return;
        }
        try {
            ragObservabilityService.dispatchAlertsForActiveUsers(24, 24, false);
        } catch (Exception ex) {
            log.warn("Scheduled RAG alert dispatch failed: {}", ex.getMessage());
        }
    }
}
