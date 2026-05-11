package com.agenticrag.knowledge.service;

import com.agenticrag.knowledge.dao.entity.IngestionTaskEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentChunkLogEntity;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DocumentIngestionWorker {

    private final IngestionTaskService ingestionTaskService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper;
    private final String workerId;

    public DocumentIngestionWorker(IngestionTaskService ingestionTaskService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   Environment environment,
                                   ObjectMapper objectMapper,
                                   KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper) {
        this.ingestionTaskService = ingestionTaskService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.knowledgeDocumentChunkLogMapper = knowledgeDocumentChunkLogMapper;
        this.workerId = buildWorkerId();
    }

    @Scheduled(fixedDelayString = "${agenticrag.ingestion.poll-delay-ms:3000}")
    public void pollAndProcess() {
        String datasourceUrl;
        try {
            datasourceUrl = environment.getProperty("spring.datasource.url");
        } catch (Exception ignored) {
            return;
        }
        if (!StringUtils.hasText(datasourceUrl) || !datasourceUrl.startsWith("jdbc:")) {
            return;
        }
        while (true) {
            IngestionTaskEntity task = ingestionTaskService.claimNextQueuedTask(workerId);
            if (task == null) {
                return;
            }
            ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ingestion-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
            ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                    () -> ingestionTaskService.renewLease(task.getId(), workerId),
                    10,
                    10,
                    TimeUnit.SECONDS);
            try {
                log.info("Processing ingestion task: taskId={}, sourceLocation={}", task.getId(), task.getSourceLocation());
                knowledgeBaseService.processDocument(task.getSourceLocation());
                Integer chunkCount = knowledgeBaseService.getDocumentChunkCount(task.getSourceLocation());
                recordPhaseNodes(task, null, chunkCount);
                ingestionTaskService.markSuccess(task, chunkCount);
            } catch (Exception ex) {
                recordPhaseNodes(task, ex, null);
                ingestionTaskService.markFailed(task, ex.getMessage());
                log.error("Ingestion task failed: taskId={}", task.getId(), ex);
            } finally {
                heartbeat.cancel(true);
                heartbeatExecutor.shutdownNow();
            }
        }
    }

    private void recordPhaseNodes(IngestionTaskEntity task, Exception failure, Integer chunkCount) {
        KnowledgeDocumentChunkLogEntity latestLog = knowledgeDocumentChunkLogMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocumentChunkLogEntity>()
                                .eq(KnowledgeDocumentChunkLogEntity::getDocId, task.getSourceLocation())
                                .orderByDesc(KnowledgeDocumentChunkLogEntity::getCreateTime)
                                .last("limit 1"))
                .stream()
                .max(Comparator.comparing(KnowledgeDocumentChunkLogEntity::getCreateTime))
                .orElse(null);

        String failedPhase = determineFailedPhase(latestLog, failure);
        List<PhaseRecord> phases = new ArrayList<>();
        phases.add(new PhaseRecord("parse", 1, latestLog == null ? null : latestLog.getExtractDuration(), "Document parsed"));
        phases.add(new PhaseRecord("chunk", 2, latestLog == null ? null : latestLog.getChunkDuration(), "Document chunked"));
        phases.add(new PhaseRecord("embed", 3, latestLog == null ? null : latestLog.getEmbedDuration(), "Embeddings generated"));
        phases.add(new PhaseRecord("persist", 4, latestLog == null ? null : latestLog.getPersistDuration(), "Chunks and vectors persisted"));

        for (PhaseRecord phase : phases) {
            boolean completed = phase.durationMs != null;
            boolean failed = phase.type.equals(failedPhase);
            if (!completed && !failed) {
                continue;
            }
            String nodeId = ingestionTaskService.startTaskNode(task, phase.type, phase.order, phase.message);
            if (failed) {
                ingestionTaskService.failTaskNode(
                        task.getId(),
                        nodeId,
                        failure == null ? "Processing failed" : failure.getMessage(),
                        writeJson(Map.of("docId", task.getSourceLocation(), "phase", phase.type)),
                        phase.durationMs == null ? 0L : phase.durationMs);
            } else {
                ingestionTaskService.completeTaskNode(
                        task.getId(),
                        nodeId,
                        phase.message,
                        writeJson(buildPhaseOutput(task, phase.type, chunkCount, latestLog)),
                        phase.durationMs == null ? 0L : phase.durationMs);
            }
        }
    }

    private Map<String, Object> buildPhaseOutput(IngestionTaskEntity task,
                                                 String phase,
                                                 Integer chunkCount,
                                                 KnowledgeDocumentChunkLogEntity latestLog) {
        if ("chunk".equals(phase) || "persist".equals(phase)) {
            return Map.of(
                    "docId", task.getSourceLocation(),
                    "chunkCount", chunkCount != null ? chunkCount : latestLog == null || latestLog.getChunkCount() == null ? 0 : latestLog.getChunkCount());
        }
        return Map.of("docId", task.getSourceLocation(), "phase", phase);
    }

    private String determineFailedPhase(KnowledgeDocumentChunkLogEntity latestLog, Exception failure) {
        if (failure == null) {
            return null;
        }
        if (latestLog == null) {
            return "parse";
        }
        if (latestLog.getExtractDuration() == null) {
            return "parse";
        }
        if (latestLog.getChunkDuration() == null) {
            return "chunk";
        }
        if (latestLog.getEmbedDuration() == null) {
            return "embed";
        }
        if (latestLog.getPersistDuration() == null) {
            return "persist";
        }
        return "persist";
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildWorkerId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        String prefix = "worker-" + runtimeName.replace('@', '-');
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String candidate = prefix + "-" + suffix;
        return candidate.length() > 64 ? candidate.substring(0, 64) : candidate;
    }

    private record PhaseRecord(String type, int order, Long durationMs, String message) {
    }
}
