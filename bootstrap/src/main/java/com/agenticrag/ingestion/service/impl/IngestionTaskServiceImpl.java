package com.agenticrag.ingestion.service.impl;

import com.agenticrag.common.ApiException;
import com.agenticrag.ingestion.dao.entity.IngestionTaskDao;
import com.agenticrag.ingestion.dao.entity.IngestionTaskNodeDao;
import com.agenticrag.ingestion.dao.mapper.IngestionTaskMapper;
import com.agenticrag.ingestion.dao.mapper.IngestionTaskNodeMapper;
import com.agenticrag.ingestion.dto.IngestionTaskDTO;
import com.agenticrag.ingestion.dto.IngestionTaskNodeDTO;
import com.agenticrag.ingestion.service.IngestionTaskService;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentDao;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class IngestionTaskServiceImpl implements IngestionTaskService {

    private static final String SOURCE_TYPE_KNOWLEDGE_DOCUMENT = "knowledge_document";
    private static final String DEFAULT_PIPELINE_ID = "knowledge-default";
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_LEASE_MINUTES = 30;

    private final IngestionTaskMapper ingestionTaskMapper;
    private final IngestionTaskNodeMapper ingestionTaskNodeMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ObjectMapper objectMapper;

    public IngestionTaskServiceImpl(IngestionTaskMapper ingestionTaskMapper,
                                    IngestionTaskNodeMapper ingestionTaskNodeMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    ObjectMapper objectMapper) {
        this.ingestionTaskMapper = ingestionTaskMapper;
        this.ingestionTaskNodeMapper = ingestionTaskNodeMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public String enqueueDocumentTask(KnowledgeDocumentDao document, String userId) {
        IngestionTaskDao existing = ingestionTaskMapper.selectOne(
                new LambdaQueryWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getSourceType, SOURCE_TYPE_KNOWLEDGE_DOCUMENT)
                        .eq(IngestionTaskDao::getSourceLocation, document.getId())
                        .eq(IngestionTaskDao::getDeleted, 0)
                        .in(IngestionTaskDao::getStatus, "QUEUED", "RUNNING")
                        .orderByDesc(IngestionTaskDao::getCreateTime)
                        .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }

        LocalDateTime now = LocalDateTime.now();
        IngestionTaskDao task = new IngestionTaskDao();
        task.setPipelineId(StringUtils.hasText(document.getPipelineId()) ? document.getPipelineId() : "knowledge-default");
        task.setSourceType(SOURCE_TYPE_KNOWLEDGE_DOCUMENT);
        task.setSourceLocation(document.getId());
        task.setSourceFileName(document.getDocName());
        task.setStatus("QUEUED");
        task.setChunkCount(0);
        task.setMetadataJson(buildMetadata(document));
        task.setRetryCount(0);
        task.setMaxRetries(DEFAULT_MAX_RETRIES);
        task.setNextRunAt(now);
        task.setCreatedBy(userId);
        task.setUpdatedBy(userId);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setDeleted(0);
        ingestionTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    @Transactional
    public IngestionTaskDao claimNextQueuedTask(String workerId) {
        LocalDateTime now = LocalDateTime.now();
        List<IngestionTaskDao> candidates = ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getSourceType, SOURCE_TYPE_KNOWLEDGE_DOCUMENT)
                        .eq(IngestionTaskDao::getDeleted, 0)
                        .in(IngestionTaskDao::getStatus, "QUEUED", "RETRYING", "RUNNING")
                        .orderByAsc(IngestionTaskDao::getCreateTime)
                        .last("limit 20"));
        for (IngestionTaskDao task : candidates) {
            if (!isClaimable(task, now)) {
                continue;
            }
            String expectedStatus = task.getStatus();
            LocalDateTime leaseUntil = now.plusMinutes(DEFAULT_LEASE_MINUTES);
            int updated = ingestionTaskMapper.update(
                    null,
                    new LambdaUpdateWrapper<IngestionTaskDao>()
                            .eq(IngestionTaskDao::getId, task.getId())
                            .eq(IngestionTaskDao::getStatus, expectedStatus)
                            .set(IngestionTaskDao::getStatus, "RUNNING")
                            .set(IngestionTaskDao::getLeaseOwner, abbreviate(workerId, 64))
                            .set(IngestionTaskDao::getLeaseUntil, leaseUntil)
                            .set(IngestionTaskDao::getStartedAt, task.getStartedAt() == null ? now : task.getStartedAt())
                            .set(IngestionTaskDao::getUpdateTime, now));
            if (updated != 1) {
                continue;
            }
            task.setStatus("RUNNING");
            task.setLeaseOwner(abbreviate(workerId, 64));
            task.setLeaseUntil(leaseUntil);
            if (task.getStartedAt() == null) {
                task.setStartedAt(now);
            }
            task.setUpdateTime(now);
            return task;
        }
        return null;
    }

    @Override
    @Transactional
    public void markRunning(IngestionTaskDao task, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getId, task.getId())
                        .set(IngestionTaskDao::getStatus, "RUNNING")
                        .set(IngestionTaskDao::getLeaseOwner, abbreviate(workerId, 64))
                        .set(IngestionTaskDao::getLeaseUntil, now.plusMinutes(DEFAULT_LEASE_MINUTES))
                        .set(IngestionTaskDao::getStartedAt, now)
                        .set(IngestionTaskDao::getUpdateTime, now));
    }

    @Override
    @Transactional
    public void renewLease(String taskId, String workerId) {
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getId, taskId)
                        .eq(IngestionTaskDao::getStatus, "RUNNING")
                        .eq(IngestionTaskDao::getLeaseOwner, abbreviate(workerId, 64))
                        .set(IngestionTaskDao::getLeaseUntil, LocalDateTime.now().plusMinutes(DEFAULT_LEASE_MINUTES))
                        .set(IngestionTaskDao::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void markSuccess(IngestionTaskDao task, Integer chunkCount) {
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getId, task.getId())
                        .set(IngestionTaskDao::getStatus, "SUCCESS")
                        .set(IngestionTaskDao::getChunkCount, chunkCount == null ? 0 : chunkCount)
                        .set(IngestionTaskDao::getErrorMessage, null)
                        .set(IngestionTaskDao::getLeaseOwner, null)
                        .set(IngestionTaskDao::getLeaseUntil, null)
                        .set(IngestionTaskDao::getNextRunAt, null)
                        .set(IngestionTaskDao::getCompletedAt, LocalDateTime.now())
                        .set(IngestionTaskDao::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void markFailed(IngestionTaskDao task, String errorMessage) {
        int currentRetryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetries = task.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : task.getMaxRetries();
        LocalDateTime now = LocalDateTime.now();
        boolean canRetry = currentRetryCount < maxRetries;
        if (canRetry) {
            int nextRetryCount = currentRetryCount + 1;
            ingestionTaskMapper.update(
                    null,
                    new LambdaUpdateWrapper<IngestionTaskDao>()
                            .eq(IngestionTaskDao::getId, task.getId())
                            .set(IngestionTaskDao::getStatus, "RETRYING")
                            .set(IngestionTaskDao::getRetryCount, nextRetryCount)
                            .set(IngestionTaskDao::getErrorMessage, abbreviate(errorMessage))
                            .set(IngestionTaskDao::getLeaseOwner, null)
                            .set(IngestionTaskDao::getLeaseUntil, null)
                            .set(IngestionTaskDao::getNextRunAt, now.plusMinutes(backoffMinutes(nextRetryCount)))
                            .set(IngestionTaskDao::getCompletedAt, null)
                            .set(IngestionTaskDao::getUpdateTime, now));
            return;
        }
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getId, task.getId())
                        .set(IngestionTaskDao::getStatus, "FAILED")
                        .set(IngestionTaskDao::getErrorMessage, abbreviate(errorMessage))
                        .set(IngestionTaskDao::getLeaseOwner, null)
                        .set(IngestionTaskDao::getLeaseUntil, null)
                        .set(IngestionTaskDao::getNextRunAt, null)
                        .set(IngestionTaskDao::getCompletedAt, now)
                        .set(IngestionTaskDao::getUpdateTime, now));
    }

    @Override
    @Transactional
    public String startTaskNode(IngestionTaskDao task, String nodeType, int nodeOrder, String message) {
        LocalDateTime now = LocalDateTime.now();
        IngestionTaskNodeDao node = new IngestionTaskNodeDao();
        node.setTaskId(task.getId());
        node.setPipelineId(task.getPipelineId());
        node.setNodeId(com.agenticrag.utils.SessionIdGenerator.generate());
        node.setNodeType(nodeType);
        node.setNodeOrder(nodeOrder);
        node.setStatus("RUNNING");
        node.setMessage(message);
        node.setCreateTime(now);
        node.setUpdateTime(now);
        node.setDeleted(0);
        ingestionTaskNodeMapper.insert(node);
        return node.getNodeId();
    }

    @Override
    @Transactional
    public void completeTaskNode(String taskId, String nodeId, String message, String outputJson, long durationMs) {
        ingestionTaskNodeMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskNodeDao>()
                        .eq(IngestionTaskNodeDao::getTaskId, taskId)
                        .eq(IngestionTaskNodeDao::getNodeId, nodeId)
                        .set(IngestionTaskNodeDao::getStatus, "SUCCESS")
                        .set(IngestionTaskNodeDao::getDurationMs, durationMs)
                        .set(IngestionTaskNodeDao::getMessage, message)
                        .set(IngestionTaskNodeDao::getOutputJson, outputJson)
                        .set(IngestionTaskNodeDao::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void failTaskNode(String taskId, String nodeId, String errorMessage, String outputJson, long durationMs) {
        ingestionTaskNodeMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskNodeDao>()
                        .eq(IngestionTaskNodeDao::getTaskId, taskId)
                        .eq(IngestionTaskNodeDao::getNodeId, nodeId)
                        .set(IngestionTaskNodeDao::getStatus, "FAILED")
                        .set(IngestionTaskNodeDao::getDurationMs, durationMs)
                        .set(IngestionTaskNodeDao::getErrorMessage, abbreviate(errorMessage))
                        .set(IngestionTaskNodeDao::getOutputJson, outputJson)
                        .set(IngestionTaskNodeDao::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public List<IngestionTaskDTO> listDocumentTasks(String docId, String userId) {
        requireDocument(docId, userId);
        return ingestionTaskMapper.selectList(
                        new LambdaQueryWrapper<IngestionTaskDao>()
                                .eq(IngestionTaskDao::getSourceType, SOURCE_TYPE_KNOWLEDGE_DOCUMENT)
                                .eq(IngestionTaskDao::getSourceLocation, docId)
                                .eq(IngestionTaskDao::getDeleted, 0)
                                .orderByDesc(IngestionTaskDao::getCreateTime))
                .stream()
                .map(this::toTaskSummary)
                .toList();
    }

    @Override
    public IngestionTaskDTO getTask(String taskId, String userId) {
        IngestionTaskDao task = requireTask(taskId);
        requireDocument(task.getSourceLocation(), userId);
        List<IngestionTaskNodeDTO> nodes = ingestionTaskNodeMapper.selectList(
                        new LambdaQueryWrapper<IngestionTaskNodeDao>()
                                .eq(IngestionTaskNodeDao::getTaskId, taskId)
                                .eq(IngestionTaskNodeDao::getDeleted, 0)
                                .orderByAsc(IngestionTaskNodeDao::getNodeOrder)
                                .orderByAsc(IngestionTaskNodeDao::getCreateTime))
                .stream()
                .map(this::toNode)
                .toList();
        return toTask(task, nodes);
    }

    @Override
    @Transactional
    public String retryTask(String taskId, String userId) {
        IngestionTaskDao task = requireTask(taskId);
        requireDocument(task.getSourceLocation(), userId);
        if (!Objects.equals(task.getStatus(), "FAILED")) {
            throw ApiException.badRequest("ingestion_task_retry_invalid", "只有失败任务才允许重试");
        }
        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getId, taskId)
                        .set(IngestionTaskDao::getStatus, "QUEUED")
                        .set(IngestionTaskDao::getRetryCount, 0)
                        .set(IngestionTaskDao::getErrorMessage, null)
                        .set(IngestionTaskDao::getLeaseOwner, null)
                        .set(IngestionTaskDao::getLeaseUntil, null)
                        .set(IngestionTaskDao::getNextRunAt, now)
                        .set(IngestionTaskDao::getCompletedAt, null)
                        .set(IngestionTaskDao::getUpdateTime, now));
        return taskId;
    }

    private String buildMetadata(KnowledgeDocumentDao document) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("docId", document.getId());
        metadata.put("kbId", document.getKbId());
        metadata.put("fileUrl", document.getFileUrl());
        metadata.put("fileType", document.getFileType());
        metadata.put("processMode", document.getProcessMode());
        metadata.put("chunkStrategy", document.getChunkStrategy());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String abbreviate(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private KnowledgeDocumentDao requireDocument(String docId, String userId) {
        KnowledgeDocumentDao document = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentDao>()
                        .eq(KnowledgeDocumentDao::getId, docId)
                        .eq(KnowledgeDocumentDao::getCreatedBy, userId)
                        .eq(KnowledgeDocumentDao::getDeleted, 0)
                        .last("limit 1"));
        if (document == null) {
            throw ApiException.notFound("knowledge_document_not_found", "知识文档不存在或无权访问");
        }
        return document;
    }

    private IngestionTaskDao requireTask(String taskId) {
        IngestionTaskDao task = ingestionTaskMapper.selectOne(
                new LambdaQueryWrapper<IngestionTaskDao>()
                        .eq(IngestionTaskDao::getId, taskId)
                        .eq(IngestionTaskDao::getDeleted, 0)
                        .last("limit 1"));
        if (task == null) {
            throw ApiException.notFound("ingestion_task_not_found", "摄取任务不存在");
        }
        return task;
    }

    private IngestionTaskDTO toTaskSummary(IngestionTaskDao task) {
        return toTask(task, List.of());
    }

    private IngestionTaskDTO toTask(IngestionTaskDao task, List<IngestionTaskNodeDTO> nodes) {
        return new IngestionTaskDTO(
                task.getId(),
                task.getSourceType(),
                task.getSourceLocation(),
                task.getSourceFileName(),
                task.getStatus(),
                task.getChunkCount(),
                task.getErrorMessage(),
                task.getMetadataJson(),
                task.getRetryCount(),
                task.getMaxRetries(),
                task.getNextRunAt(),
                task.getLeaseOwner(),
                task.getLeaseUntil(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getCreateTime(),
                task.getUpdateTime(),
                nodes
        );
    }

    private IngestionTaskNodeDTO toNode(IngestionTaskNodeDao node) {
        return new IngestionTaskNodeDTO(
                node.getNodeId(),
                node.getNodeType(),
                node.getNodeOrder(),
                node.getStatus(),
                node.getDurationMs(),
                node.getMessage(),
                node.getErrorMessage(),
                node.getOutputJson()
        );
    }

    private boolean isClaimable(IngestionTaskDao task, LocalDateTime now) {
        if (Objects.equals(task.getStatus(), "QUEUED")) {
            return task.getNextRunAt() == null || !task.getNextRunAt().isAfter(now);
        }
        if (Objects.equals(task.getStatus(), "RETRYING")) {
            return task.getNextRunAt() == null || !task.getNextRunAt().isAfter(now);
        }
        if (Objects.equals(task.getStatus(), "RUNNING")) {
            return task.getLeaseUntil() != null && !task.getLeaseUntil().isAfter(now);
        }
        return false;
    }

    private long backoffMinutes(int retryCount) {
        return switch (retryCount) {
            case 1 -> 1L;
            case 2 -> 5L;
            default -> 15L;
        };
    }
}
