package com.agenticrag.knowledge.service.impl;

import com.agenticrag.common.ApiException;
import com.agenticrag.knowledge.dao.entity.IngestionTaskEntity;
import com.agenticrag.knowledge.dao.entity.IngestionTaskNodeEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.agenticrag.knowledge.dao.mapper.IngestionTaskMapper;
import com.agenticrag.knowledge.dao.mapper.IngestionTaskNodeMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.agenticrag.knowledge.dto.IngestionTaskDTO;
import com.agenticrag.knowledge.dto.IngestionTaskNodeDTO;
import com.agenticrag.knowledge.service.IngestionTaskService;
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
    public String enqueueDocumentTask(KnowledgeDocumentEntity document, String userId) {
        IngestionTaskEntity existing = ingestionTaskMapper.selectOne(
                new LambdaQueryWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getSourceType, SOURCE_TYPE_KNOWLEDGE_DOCUMENT)
                        .eq(IngestionTaskEntity::getSourceLocation, document.getId())
                        .eq(IngestionTaskEntity::getDeleted, 0)
                        .in(IngestionTaskEntity::getStatus, "QUEUED", "RUNNING")
                        .orderByDesc(IngestionTaskEntity::getCreateTime)
                        .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }

        LocalDateTime now = LocalDateTime.now();
        IngestionTaskEntity task = new IngestionTaskEntity();
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
    public IngestionTaskEntity claimNextQueuedTask(String workerId) {
        LocalDateTime now = LocalDateTime.now();
        List<IngestionTaskEntity> candidates = ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getSourceType, SOURCE_TYPE_KNOWLEDGE_DOCUMENT)
                        .eq(IngestionTaskEntity::getDeleted, 0)
                        .in(IngestionTaskEntity::getStatus, "QUEUED", "RETRYING", "RUNNING")
                        .orderByAsc(IngestionTaskEntity::getCreateTime)
                        .last("limit 20"));
        for (IngestionTaskEntity task : candidates) {
            if (!isClaimable(task, now)) {
                continue;
            }
            String expectedStatus = task.getStatus();
            LocalDateTime leaseUntil = now.plusMinutes(DEFAULT_LEASE_MINUTES);
            int updated = ingestionTaskMapper.update(
                    null,
                    new LambdaUpdateWrapper<IngestionTaskEntity>()
                            .eq(IngestionTaskEntity::getId, task.getId())
                            .eq(IngestionTaskEntity::getStatus, expectedStatus)
                            .set(IngestionTaskEntity::getStatus, "RUNNING")
                            .set(IngestionTaskEntity::getLeaseOwner, abbreviate(workerId, 64))
                            .set(IngestionTaskEntity::getLeaseUntil, leaseUntil)
                            .set(IngestionTaskEntity::getStartedAt, task.getStartedAt() == null ? now : task.getStartedAt())
                            .set(IngestionTaskEntity::getUpdateTime, now));
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
    public void markRunning(IngestionTaskEntity task, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getId, task.getId())
                        .set(IngestionTaskEntity::getStatus, "RUNNING")
                        .set(IngestionTaskEntity::getLeaseOwner, abbreviate(workerId, 64))
                        .set(IngestionTaskEntity::getLeaseUntil, now.plusMinutes(DEFAULT_LEASE_MINUTES))
                        .set(IngestionTaskEntity::getStartedAt, now)
                        .set(IngestionTaskEntity::getUpdateTime, now));
    }

    @Override
    @Transactional
    public void renewLease(String taskId, String workerId) {
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getId, taskId)
                        .eq(IngestionTaskEntity::getStatus, "RUNNING")
                        .eq(IngestionTaskEntity::getLeaseOwner, abbreviate(workerId, 64))
                        .set(IngestionTaskEntity::getLeaseUntil, LocalDateTime.now().plusMinutes(DEFAULT_LEASE_MINUTES))
                        .set(IngestionTaskEntity::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void markSuccess(IngestionTaskEntity task, Integer chunkCount) {
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getId, task.getId())
                        .set(IngestionTaskEntity::getStatus, "SUCCESS")
                        .set(IngestionTaskEntity::getChunkCount, chunkCount == null ? 0 : chunkCount)
                        .set(IngestionTaskEntity::getErrorMessage, null)
                        .set(IngestionTaskEntity::getLeaseOwner, null)
                        .set(IngestionTaskEntity::getLeaseUntil, null)
                        .set(IngestionTaskEntity::getNextRunAt, null)
                        .set(IngestionTaskEntity::getCompletedAt, LocalDateTime.now())
                        .set(IngestionTaskEntity::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void markFailed(IngestionTaskEntity task, String errorMessage) {
        int currentRetryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetries = task.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : task.getMaxRetries();
        LocalDateTime now = LocalDateTime.now();
        boolean canRetry = currentRetryCount < maxRetries;
        if (canRetry) {
            int nextRetryCount = currentRetryCount + 1;
            ingestionTaskMapper.update(
                    null,
                    new LambdaUpdateWrapper<IngestionTaskEntity>()
                            .eq(IngestionTaskEntity::getId, task.getId())
                            .set(IngestionTaskEntity::getStatus, "RETRYING")
                            .set(IngestionTaskEntity::getRetryCount, nextRetryCount)
                            .set(IngestionTaskEntity::getErrorMessage, abbreviate(errorMessage))
                            .set(IngestionTaskEntity::getLeaseOwner, null)
                            .set(IngestionTaskEntity::getLeaseUntil, null)
                            .set(IngestionTaskEntity::getNextRunAt, now.plusMinutes(backoffMinutes(nextRetryCount)))
                            .set(IngestionTaskEntity::getCompletedAt, null)
                            .set(IngestionTaskEntity::getUpdateTime, now));
            return;
        }
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getId, task.getId())
                        .set(IngestionTaskEntity::getStatus, "FAILED")
                        .set(IngestionTaskEntity::getErrorMessage, abbreviate(errorMessage))
                        .set(IngestionTaskEntity::getLeaseOwner, null)
                        .set(IngestionTaskEntity::getLeaseUntil, null)
                        .set(IngestionTaskEntity::getNextRunAt, null)
                        .set(IngestionTaskEntity::getCompletedAt, now)
                        .set(IngestionTaskEntity::getUpdateTime, now));
    }

    @Override
    @Transactional
    public String startTaskNode(IngestionTaskEntity task, String nodeType, int nodeOrder, String message) {
        LocalDateTime now = LocalDateTime.now();
        IngestionTaskNodeEntity node = new IngestionTaskNodeEntity();
        node.setTaskId(task.getId());
        node.setPipelineId(task.getPipelineId());
        node.setNodeId(com.agenticrag.common.SessionIdGenerator.generate());
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
                new LambdaUpdateWrapper<IngestionTaskNodeEntity>()
                        .eq(IngestionTaskNodeEntity::getTaskId, taskId)
                        .eq(IngestionTaskNodeEntity::getNodeId, nodeId)
                        .set(IngestionTaskNodeEntity::getStatus, "SUCCESS")
                        .set(IngestionTaskNodeEntity::getDurationMs, durationMs)
                        .set(IngestionTaskNodeEntity::getMessage, message)
                        .set(IngestionTaskNodeEntity::getOutputJson, outputJson)
                        .set(IngestionTaskNodeEntity::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void failTaskNode(String taskId, String nodeId, String errorMessage, String outputJson, long durationMs) {
        ingestionTaskNodeMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskNodeEntity>()
                        .eq(IngestionTaskNodeEntity::getTaskId, taskId)
                        .eq(IngestionTaskNodeEntity::getNodeId, nodeId)
                        .set(IngestionTaskNodeEntity::getStatus, "FAILED")
                        .set(IngestionTaskNodeEntity::getDurationMs, durationMs)
                        .set(IngestionTaskNodeEntity::getErrorMessage, abbreviate(errorMessage))
                        .set(IngestionTaskNodeEntity::getOutputJson, outputJson)
                        .set(IngestionTaskNodeEntity::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public List<IngestionTaskDTO> listDocumentTasks(String docId, String userId) {
        requireDocument(docId, userId);
        return ingestionTaskMapper.selectList(
                        new LambdaQueryWrapper<IngestionTaskEntity>()
                                .eq(IngestionTaskEntity::getSourceType, SOURCE_TYPE_KNOWLEDGE_DOCUMENT)
                                .eq(IngestionTaskEntity::getSourceLocation, docId)
                                .eq(IngestionTaskEntity::getDeleted, 0)
                                .orderByDesc(IngestionTaskEntity::getCreateTime))
                .stream()
                .map(this::toTaskSummary)
                .toList();
    }

    @Override
    public IngestionTaskDTO getTask(String taskId, String userId) {
        IngestionTaskEntity task = requireTask(taskId);
        requireDocument(task.getSourceLocation(), userId);
        List<IngestionTaskNodeDTO> nodes = ingestionTaskNodeMapper.selectList(
                        new LambdaQueryWrapper<IngestionTaskNodeEntity>()
                                .eq(IngestionTaskNodeEntity::getTaskId, taskId)
                                .eq(IngestionTaskNodeEntity::getDeleted, 0)
                                .orderByAsc(IngestionTaskNodeEntity::getNodeOrder)
                                .orderByAsc(IngestionTaskNodeEntity::getCreateTime))
                .stream()
                .map(this::toNode)
                .toList();
        return toTask(task, nodes);
    }

    @Override
    @Transactional
    public String retryTask(String taskId, String userId) {
        IngestionTaskEntity task = requireTask(taskId);
        requireDocument(task.getSourceLocation(), userId);
        if (!Objects.equals(task.getStatus(), "FAILED")) {
            throw ApiException.badRequest("ingestion_task_retry_invalid", "只有失败任务才允许重试");
        }
        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.update(
                null,
                new LambdaUpdateWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getId, taskId)
                        .set(IngestionTaskEntity::getStatus, "QUEUED")
                        .set(IngestionTaskEntity::getRetryCount, 0)
                        .set(IngestionTaskEntity::getErrorMessage, null)
                        .set(IngestionTaskEntity::getLeaseOwner, null)
                        .set(IngestionTaskEntity::getLeaseUntil, null)
                        .set(IngestionTaskEntity::getNextRunAt, now)
                        .set(IngestionTaskEntity::getCompletedAt, null)
                        .set(IngestionTaskEntity::getUpdateTime, now));
        return taskId;
    }

    private String buildMetadata(KnowledgeDocumentEntity document) {
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

    private KnowledgeDocumentEntity requireDocument(String docId, String userId) {
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getId, docId)
                        .eq(KnowledgeDocumentEntity::getCreatedBy, userId)
                        .eq(KnowledgeDocumentEntity::getDeleted, 0)
                        .last("limit 1"));
        if (document == null) {
            throw ApiException.notFound("knowledge_document_not_found", "知识文档不存在或无权访问");
        }
        return document;
    }

    private IngestionTaskEntity requireTask(String taskId) {
        IngestionTaskEntity task = ingestionTaskMapper.selectOne(
                new LambdaQueryWrapper<IngestionTaskEntity>()
                        .eq(IngestionTaskEntity::getId, taskId)
                        .eq(IngestionTaskEntity::getDeleted, 0)
                        .last("limit 1"));
        if (task == null) {
            throw ApiException.notFound("ingestion_task_not_found", "摄取任务不存在");
        }
        return task;
    }

    private IngestionTaskDTO toTaskSummary(IngestionTaskEntity task) {
        return toTask(task, List.of());
    }

    private IngestionTaskDTO toTask(IngestionTaskEntity task, List<IngestionTaskNodeDTO> nodes) {
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

    private IngestionTaskNodeDTO toNode(IngestionTaskNodeEntity node) {
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

    private boolean isClaimable(IngestionTaskEntity task, LocalDateTime now) {
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
