package com.agenticrag.controller;

import com.agenticrag.observability.dto.RagAlertDispatchResultDTO;
import com.agenticrag.observability.dto.RagObservabilityAlertDTO;
import com.agenticrag.observability.dto.RagObservabilityMetricsDTO;
import com.agenticrag.observability.dto.RagObservabilitySummaryDTO;
import com.agenticrag.observability.service.RagObservabilityService;
import com.agenticrag.user.auth.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rag/observability")
public class RagObservabilityController {

    private final RagObservabilityService ragObservabilityService;

    public RagObservabilityController(RagObservabilityService ragObservabilityService) {
        this.ragObservabilityService = ragObservabilityService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<RagObservabilityMetricsDTO> metrics(@CurrentUser String userId,
                                                              @RequestParam(name = "hours", required = false) Integer hours) {
        return ResponseEntity.ok(ragObservabilityService.getMetrics(userId, hours == null ? 24 : hours));
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<RagObservabilityAlertDTO>> alerts(@CurrentUser String userId,
                                                                 @RequestParam(name = "hours", required = false) Integer hours,
                                                                 @RequestParam(name = "baselineHours", required = false) Integer baselineHours) {
        return ResponseEntity.ok(ragObservabilityService.getAlerts(
                userId,
                hours == null ? 24 : hours,
                baselineHours == null ? 24 : baselineHours));
    }

    @GetMapping("/summary")
    public ResponseEntity<RagObservabilitySummaryDTO> summary(@CurrentUser String userId,
                                                              @RequestParam(name = "hours", required = false) Integer hours,
                                                              @RequestParam(name = "baselineHours", required = false) Integer baselineHours) {
        return ResponseEntity.ok(ragObservabilityService.getSummary(
                userId,
                hours == null ? 24 : hours,
                baselineHours == null ? 24 : baselineHours));
    }

    @PostMapping("/alerts/dispatch")
    public ResponseEntity<RagAlertDispatchResultDTO> dispatch(@CurrentUser String userId,
                                                              @RequestParam(name = "hours", required = false) Integer hours,
                                                              @RequestParam(name = "baselineHours", required = false) Integer baselineHours,
                                                              @RequestParam(name = "force", required = false) Boolean force) {
        return ResponseEntity.ok(ragObservabilityService.dispatchAlerts(
                userId,
                hours == null ? 24 : hours,
                baselineHours == null ? 24 : baselineHours,
                force != null && force));
    }
}
