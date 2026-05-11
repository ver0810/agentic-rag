package com.agenticrag.observability.service.impl;

import com.agenticrag.knowledge.dao.entity.IngestionTaskEntity;
import com.agenticrag.knowledge.dao.mapper.IngestionTaskMapper;
import com.agenticrag.infra.ai.config.AiObservabilityProperties;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.knowledge.dao.entity.KnowledgeChunkEntity;
import com.agenticrag.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.agenticrag.observability.dto.RagAlertDispatchResultDTO;
import com.agenticrag.observability.dto.RagObservabilityAlertDTO;
import com.agenticrag.observability.dto.RagObservabilityMetricsDTO;
import com.agenticrag.observability.dto.RagObservabilitySummaryDTO;
import com.agenticrag.observability.service.RagAlertNotifier;
import com.agenticrag.observability.service.RagObservabilityService;
import com.agenticrag.ragtrace.dao.entity.RagTraceNodeEntity;
import com.agenticrag.ragtrace.dao.entity.RagTraceRunEntity;
import com.agenticrag.ragtrace.dao.mapper.RagTraceNodeMapper;
import com.agenticrag.ragtrace.dao.mapper.RagTraceRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class RagObservabilityServiceImpl implements RagObservabilityService {

    private final RagTraceRunMapper ragTraceRunMapper;
    private final RagTraceNodeMapper ragTraceNodeMapper;
    private final IngestionTaskMapper ingestionTaskMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final AiObservabilityProperties observabilityProperties;
    private final TokenCostEstimator tokenCostEstimator;
    private final ObjectMapper objectMapper;
    private final RagAlertNotifier ragAlertNotifier;

    public RagObservabilityServiceImpl(RagTraceRunMapper ragTraceRunMapper,
                                       RagTraceNodeMapper ragTraceNodeMapper,
                                       IngestionTaskMapper ingestionTaskMapper,
                                       KnowledgeChunkMapper knowledgeChunkMapper,
                                       AiObservabilityProperties observabilityProperties,
                                       TokenCostEstimator tokenCostEstimator,
                                       ObjectMapper objectMapper,
                                       RagAlertNotifier ragAlertNotifier) {
        this.ragTraceRunMapper = ragTraceRunMapper;
        this.ragTraceNodeMapper = ragTraceNodeMapper;
        this.ingestionTaskMapper = ingestionTaskMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.observabilityProperties = observabilityProperties;
        this.tokenCostEstimator = tokenCostEstimator;
        this.objectMapper = objectMapper;
        this.ragAlertNotifier = ragAlertNotifier;
    }

    @Override
    public RagObservabilityMetricsDTO getMetrics(String userId, int hours) {
        LocalDateTime now = LocalDateTime.now();
        int effectiveHours = normalizeHours(hours);
        Window window = new Window(now.minusHours(effectiveHours), now);
        return buildMetrics(userId, window);
    }

    @Override
    public List<RagObservabilityAlertDTO> getAlerts(String userId, int hours, int baselineHours) {
        LocalDateTime now = LocalDateTime.now();
        int effectiveHours = normalizeHours(hours);
        int effectiveBaselineHours = normalizeHours(baselineHours);
        Window currentWindow = new Window(now.minusHours(effectiveHours), now);
        Window baselineWindow = new Window(currentWindow.start().minusHours(effectiveBaselineHours), currentWindow.start());

        WindowData current = loadWindowData(userId, currentWindow);
        WindowData baseline = loadWindowData(userId, baselineWindow);

        List<RagObservabilityAlertDTO> alerts = new ArrayList<>();
        alerts.add(buildConsecutiveFailureAlert(userId));
        alerts.add(buildRetrievalDeclineAlert(current, baseline));
        alerts.add(buildModelErrorAlert(current, baseline));
        return alerts;
    }

    @Override
    public RagObservabilitySummaryDTO getSummary(String userId, int hours, int baselineHours) {
        return new RagObservabilitySummaryDTO(
                getMetrics(userId, hours),
                getAlerts(userId, hours, baselineHours));
    }

    @Override
    public RagAlertDispatchResultDTO dispatchAlerts(String userId, int hours, int baselineHours, boolean forceDispatch) {
        int effectiveHours = normalizeHours(hours);
        int effectiveBaselineHours = normalizeHours(baselineHours);
        RagObservabilityMetricsDTO metrics = getMetrics(userId, effectiveHours);
        List<RagObservabilityAlertDTO> alerts = getAlerts(userId, effectiveHours, effectiveBaselineHours);
        return ragAlertNotifier.notify(userId, metrics, alerts, forceDispatch);
    }

    @Override
    public List<RagAlertDispatchResultDTO> dispatchAlertsForActiveUsers(int hours, int baselineHours, boolean forceDispatch) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(normalizeHours(hours));
        List<String> users = new ArrayList<>();
        ragTraceRunMapper.selectList(new LambdaQueryWrapper<RagTraceRunEntity>()
                        .select(RagTraceRunEntity::getUserId)
                        .eq(RagTraceRunEntity::getTraceName, "rag_query")
                        .eq(RagTraceRunEntity::getDeleted, 0)
                        .ge(RagTraceRunEntity::getCreateTime, start))
                .stream()
                .map(RagTraceRunEntity::getUserId)
                .filter(Objects::nonNull)
                .forEach(users::add);
        ingestionTaskMapper.selectList(new LambdaQueryWrapper<IngestionTaskEntity>()
                        .select(IngestionTaskEntity::getCreatedBy)
                        .eq(IngestionTaskEntity::getSourceType, "knowledge_document")
                        .eq(IngestionTaskEntity::getDeleted, 0)
                        .ge(IngestionTaskEntity::getCreateTime, start))
                .stream()
                .map(IngestionTaskEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .forEach(users::add);

        return users.stream()
                .distinct()
                .map(userId -> dispatchAlerts(userId, hours, baselineHours, forceDispatch))
                .filter(result -> result.activeAlertCount() > 0 || result.dispatched())
                .toList();
    }

    private RagObservabilityMetricsDTO buildMetrics(String userId, Window window) {
        WindowData data = loadWindowData(userId, window);
        return data.metrics();
    }

    private WindowData loadWindowData(String userId, Window window) {
        List<RagTraceRunEntity> runs = loadTraceRuns(userId, window);
        Map<String, List<RagTraceNodeEntity>> nodesByTraceId = loadTraceNodes(runs);
        List<IngestionTaskEntity> tasks = loadIngestionTasks(userId, window);

        long totalQueries = runs.size();
        long successfulQueries = runs.stream().filter(run -> Objects.equals(run.getStatus(), "SUCCESS")).count();
        long failedQueries = runs.stream().filter(run -> Objects.equals(run.getStatus(), "ERROR")).count();

        long emptyRetrievalCount = 0L;
        long refusalCount = 0L;
        long totalDurationMs = 0L;
        long durationCount = 0L;
        long estimatedChatInputTokens = 0L;
        long estimatedChatOutputTokens = 0L;
        long estimatedQueryEmbeddingTokens = 0L;
        double estimatedChatCost = 0d;
        double estimatedQueryEmbeddingCost = 0d;
        long generateErrorCount = 0L;

        for (RagTraceRunEntity run : runs) {
            Map<String, Object> extraData = parseJson(run.getExtraData());
            String answerState = stringValue(extraData, "answerState");
            if ("empty_retrieval".equals(answerState)) {
                emptyRetrievalCount++;
            }
            if ("empty_retrieval".equals(answerState) || "refused".equals(answerState)) {
                refusalCount++;
            }
            if (run.getDurationMs() != null && run.getDurationMs() > 0) {
                totalDurationMs += run.getDurationMs();
                durationCount++;
            }
            estimatedChatInputTokens += longValue(extraData, "estimatedChatInputTokens");
            estimatedChatOutputTokens += longValue(extraData, "estimatedChatOutputTokens");
            estimatedQueryEmbeddingTokens += longValue(extraData, "estimatedEmbeddingTokens");
            estimatedChatCost += doubleValue(extraData, "estimatedChatInputCost");
            estimatedChatCost += doubleValue(extraData, "estimatedChatOutputCost");
            estimatedQueryEmbeddingCost += doubleValue(extraData, "estimatedEmbeddingCost");

            List<RagTraceNodeEntity> nodes = nodesByTraceId.getOrDefault(run.getTraceId(), List.of());
            boolean hasGenerateError = nodes.stream()
                    .anyMatch(node -> Objects.equals(node.getNodeType(), "generate") && Objects.equals(node.getStatus(), "ERROR"));
            if (hasGenerateError) {
                generateErrorCount++;
            }
        }

        long totalIngestionTasks = tasks.size();
        long terminalIngestionTasks = tasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), "SUCCESS") || Objects.equals(task.getStatus(), "FAILED"))
                .count();
        long successfulIngestionTasks = tasks.stream()
                .filter(task -> Objects.equals(task.getStatus(), "SUCCESS"))
                .count();
        long retryingTasks = tasks.stream()
                .filter(task -> task.getRetryCount() != null && task.getRetryCount() > 0)
                .count();

        long totalIngestionDurationMs = 0L;
        long ingestionDurationCount = 0L;
        Set<String> successfulDocIds = new LinkedHashSet<>();
        for (IngestionTaskEntity task : tasks) {
            if (task.getStartedAt() != null && task.getCompletedAt() != null) {
                totalIngestionDurationMs += Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis();
                ingestionDurationCount++;
            }
            if (Objects.equals(task.getStatus(), "SUCCESS") && task.getSourceLocation() != null) {
                successfulDocIds.add(task.getSourceLocation());
            }
        }

        long estimatedIngestionEmbeddingTokens = loadIngestionEmbeddingTokens(successfulDocIds);
        double estimatedIngestionEmbeddingCost = tokenCostEstimator.estimateEmbeddingCost(estimatedIngestionEmbeddingTokens);

        double emptyRetrievalRate = ratio(emptyRetrievalCount, successfulQueries);
        double refusalRate = ratio(refusalCount, successfulQueries);
        double averageResponseTimeMs = durationCount == 0 ? 0d : round2((double) totalDurationMs / durationCount);
        double modelErrorRate = ratio(generateErrorCount, totalQueries);
        double documentProcessingSuccessRate = ratio(successfulIngestionTasks, terminalIngestionTasks);
        double ingestionRetryRate = ratio(retryingTasks, totalIngestionTasks);
        double averageIngestionDurationMs = ingestionDurationCount == 0 ? 0d : round2((double) totalIngestionDurationMs / ingestionDurationCount);

        long estimatedTotalTokens = estimatedChatInputTokens + estimatedChatOutputTokens + estimatedQueryEmbeddingTokens + estimatedIngestionEmbeddingTokens;
        double estimatedEmbeddingCost = tokenCostEstimator.round6(estimatedQueryEmbeddingCost + estimatedIngestionEmbeddingCost);
        double estimatedTotalCost = tokenCostEstimator.round6(estimatedChatCost + estimatedEmbeddingCost);

        RagObservabilityMetricsDTO metrics = new RagObservabilityMetricsDTO(
                window.start(),
                window.end(),
                totalQueries,
                successfulQueries,
                failedQueries,
                emptyRetrievalRate,
                refusalRate,
                averageResponseTimeMs,
                modelErrorRate,
                totalIngestionTasks,
                terminalIngestionTasks,
                documentProcessingSuccessRate,
                ingestionRetryRate,
                averageIngestionDurationMs,
                estimatedChatInputTokens,
                estimatedChatOutputTokens,
                estimatedQueryEmbeddingTokens,
                estimatedIngestionEmbeddingTokens,
                estimatedTotalTokens,
                tokenCostEstimator.round6(estimatedChatCost),
                estimatedEmbeddingCost,
                estimatedTotalCost
        );
        return new WindowData(window, runs, nodesByTraceId, tasks, metrics);
    }

    private RagObservabilityAlertDTO buildConsecutiveFailureAlert(String userId) {
        List<IngestionTaskEntity> recentTasks = ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getCreatedBy, userId)
                        .eq(IngestionTaskEntity::getSourceType, "knowledge_document")
                        .eq(IngestionTaskEntity::getDeleted, 0)
                        .orderByDesc(IngestionTaskEntity::getCreateTime)
                        .last("limit 10"));
        int streak = 0;
        for (IngestionTaskEntity task : recentTasks) {
            if (Objects.equals(task.getStatus(), "FAILED")) {
                streak++;
            } else {
                break;
            }
        }
        int threshold = observabilityProperties.getAlerts().getConsecutiveFailuresThreshold();
        String status = streak >= threshold ? "ACTIVE" : "OK";
        String level = streak >= threshold ? "critical" : "info";
        return new RagObservabilityAlertDTO(
                "ingestion_consecutive_failures",
                level,
                status,
                streak >= threshold
                        ? "文档处理任务连续失败次数超过阈值"
                        : "文档处理任务连续失败次数正常",
                (double) streak,
                null,
                (double) threshold,
                Map.of("recentTaskCount", recentTasks.size(), "consecutiveFailures", streak));
    }

    private RagObservabilityAlertDTO buildRetrievalDeclineAlert(WindowData current, WindowData baseline) {
        long currentSamples = current.metrics().successfulQueries();
        long baselineSamples = baseline.metrics().successfulQueries();
        int minSamples = observabilityProperties.getAlerts().getMinimumSampleSize();
        double currentSuccessRate = 1d - current.metrics().emptyRetrievalRate();
        double baselineSuccessRate = 1d - baseline.metrics().emptyRetrievalRate();
        double dropThreshold = observabilityProperties.getAlerts().getRetrievalSuccessDropThreshold();

        if (currentSamples < minSamples || baselineSamples < minSamples) {
            return new RagObservabilityAlertDTO(
                    "retrieval_quality_decline",
                    "info",
                    "INSUFFICIENT_DATA",
                    "召回异常下降告警样本不足",
                    currentSuccessRate,
                    baselineSuccessRate,
                    dropThreshold,
                    Map.of("currentSamples", currentSamples, "baselineSamples", baselineSamples));
        }

        double drop = baselineSuccessRate - currentSuccessRate;
        boolean active = drop >= dropThreshold;
        return new RagObservabilityAlertDTO(
                "retrieval_quality_decline",
                active ? "warning" : "info",
                active ? "ACTIVE" : "OK",
                active ? "召回成功率较基线明显下降" : "召回成功率未出现异常下降",
                currentSuccessRate,
                baselineSuccessRate,
                dropThreshold,
                Map.of(
                        "currentEmptyRetrievalRate", current.metrics().emptyRetrievalRate(),
                        "baselineEmptyRetrievalRate", baseline.metrics().emptyRetrievalRate(),
                        "successRateDrop", round4(drop)));
    }

    private RagObservabilityAlertDTO buildModelErrorAlert(WindowData current, WindowData baseline) {
        long currentSamples = current.metrics().totalQueries();
        long baselineSamples = baseline.metrics().totalQueries();
        int minSamples = observabilityProperties.getAlerts().getMinimumSampleSize();
        double currentErrorRate = current.metrics().modelErrorRate();
        double baselineErrorRate = baseline.metrics().modelErrorRate();
        double absoluteThreshold = observabilityProperties.getAlerts().getModelErrorRateThreshold();
        double increaseThreshold = observabilityProperties.getAlerts().getModelErrorRateIncreaseThreshold();

        if (currentSamples < minSamples || baselineSamples < minSamples) {
            return new RagObservabilityAlertDTO(
                    "model_error_rate_spike",
                    "info",
                    "INSUFFICIENT_DATA",
                    "模型调用异常上升告警样本不足",
                    currentErrorRate,
                    baselineErrorRate,
                    absoluteThreshold,
                    Map.of("currentSamples", currentSamples, "baselineSamples", baselineSamples));
        }

        double increase = currentErrorRate - baselineErrorRate;
        boolean active = currentErrorRate >= absoluteThreshold && increase >= increaseThreshold;
        return new RagObservabilityAlertDTO(
                "model_error_rate_spike",
                active ? "warning" : "info",
                active ? "ACTIVE" : "OK",
                active ? "模型调用异常率明显上升" : "模型调用异常率未出现异常上升",
                currentErrorRate,
                baselineErrorRate,
                absoluteThreshold,
                Map.of("errorRateIncrease", round4(increase), "increaseThreshold", increaseThreshold));
    }

    private List<RagTraceRunEntity> loadTraceRuns(String userId, Window window) {
        return ragTraceRunMapper.selectList(
                new LambdaQueryWrapper<RagTraceRunEntity>()
                        .eq(RagTraceRunEntity::getUserId, userId)
                        .eq(RagTraceRunEntity::getTraceName, "rag_query")
                        .eq(RagTraceRunEntity::getDeleted, 0)
                        .ge(RagTraceRunEntity::getCreateTime, window.start())
                        .lt(RagTraceRunEntity::getCreateTime, window.end())
                        .orderByDesc(RagTraceRunEntity::getCreateTime));
    }

    private Map<String, List<RagTraceNodeEntity>> loadTraceNodes(List<RagTraceRunEntity> runs) {
        if (runs.isEmpty()) {
            return Map.of();
        }
        List<String> traceIds = runs.stream().map(RagTraceRunEntity::getTraceId).toList();
        List<RagTraceNodeEntity> nodes = ragTraceNodeMapper.selectList(
                new LambdaQueryWrapper<RagTraceNodeEntity>()
                        .in(RagTraceNodeEntity::getTraceId, traceIds)
                        .eq(RagTraceNodeEntity::getDeleted, 0)
                        .orderByAsc(RagTraceNodeEntity::getCreateTime));
        Map<String, List<RagTraceNodeEntity>> grouped = new LinkedHashMap<>();
        for (RagTraceNodeEntity node : nodes) {
            grouped.computeIfAbsent(node.getTraceId(), ignored -> new ArrayList<>()).add(node);
        }
        return grouped;
    }

    private List<IngestionTaskEntity> loadIngestionTasks(String userId, Window window) {
        return ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getCreatedBy, userId)
                        .eq(IngestionTaskEntity::getSourceType, "knowledge_document")
                        .eq(IngestionTaskEntity::getDeleted, 0)
                        .ge(IngestionTaskEntity::getCreateTime, window.start())
                        .lt(IngestionTaskEntity::getCreateTime, window.end())
                        .orderByDesc(IngestionTaskEntity::getCreateTime));
    }

    private long loadIngestionEmbeddingTokens(Collection<String> docIds) {
        if (docIds.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (List<String> batch : batches(new ArrayList<>(docIds), 200)) {
            List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunkEntity>()
                            .in(KnowledgeChunkEntity::getDocId, batch)
                            .eq(KnowledgeChunkEntity::getDeleted, 0));
            for (KnowledgeChunkEntity chunk : chunks) {
                if (chunk.getTokenCount() != null && chunk.getTokenCount() > 0) {
                    total += chunk.getTokenCount();
                } else if (chunk.getCharCount() != null && chunk.getCharCount() > 0) {
                    total += tokenCostEstimator.estimateTokensByCharCount(chunk.getCharCount());
                } else if (chunk.getContent() != null) {
                    total += tokenCostEstimator.estimateTokens(chunk.getContent());
                }
            }
        }
        return total;
    }

    private List<List<String>> batches(List<String> values, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < values.size(); start += batchSize) {
            batches.add(values.subList(start, Math.min(values.size(), start + batchSize)));
        }
        return batches;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String stringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private double doubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0d;
            }
        }
        return 0d;
    }

    private int normalizeHours(int hours) {
        return hours <= 0 ? 24 : Math.min(hours, 24 * 30);
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return round4((double) numerator / denominator);
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private double round4(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    private record Window(LocalDateTime start, LocalDateTime end) {
    }

    private record WindowData(
            Window window,
            List<RagTraceRunEntity> runs,
            Map<String, List<RagTraceNodeEntity>> nodesByTraceId,
            List<IngestionTaskEntity> tasks,
            RagObservabilityMetricsDTO metrics
    ) {
    }
}
