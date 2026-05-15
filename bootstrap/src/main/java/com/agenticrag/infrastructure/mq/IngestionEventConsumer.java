package com.agenticrag.infrastructure.mq;

import com.agenticrag.framework.infrastructure.mq.MqEvent;
import com.agenticrag.knowledge.dao.entity.IngestionTaskEntity;
import com.agenticrag.knowledge.dao.mapper.IngestionTaskMapper;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${EVENT_INGESTION_TOPIC:rag-ingestion-topic}",
        consumerGroup = "${EVENT_INGESTION_CG:rag-ingestion-cg}")
public class IngestionEventConsumer implements RocketMQListener<String> {

    private final KnowledgeBaseService knowledgeBaseService;
    private final IngestionTaskMapper ingestionTaskMapper;
    private final ObjectMapper objectMapper;

    public IngestionEventConsumer(KnowledgeBaseService knowledgeBaseService,
                                  IngestionTaskMapper ingestionTaskMapper,
                                  ObjectMapper objectMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.ingestionTaskMapper = ingestionTaskMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void onMessage(String message) {
        MqEvent event;
        try {
            event = objectMapper.readValue(message, MqEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize ingestion event: {}", message, e);
            return;
        }

        String docId = event.payload() != null ? event.payload().get("docId") : null;
        String taskId = event.payload() != null ? event.payload().get("taskId") : null;
        if (docId == null) {
            log.warn("Received ingestion event without docId: eventId={}", event.eventId());
            return;
        }

        log.info("Received ingestion event: docId={}, taskId={}, eventId={}", docId, taskId, event.eventId());
        try {
            knowledgeBaseService.processDocument(docId);
            Integer chunkCount = knowledgeBaseService.getDocumentChunkCount(docId);
            markTaskSuccess(taskId, chunkCount);
            log.info("Ingestion completed: docId={}, chunkCount={}", docId, chunkCount);
        } catch (DuplicateKeyException e) {
            log.warn("Duplicate ingestion detected, skipping: docId={}", docId);
        } catch (Exception e) {
            log.error("Ingestion failed: docId={}", docId, e);
            markTaskFailed(taskId, e.getMessage());
            throw new RuntimeException("Ingestion processing failed for: " + docId, e);
        }
    }

    private void markTaskSuccess(String taskId, Integer chunkCount) {
        if (taskId == null) return;
        try {
            IngestionTaskEntity task = ingestionTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("SUCCESS");
                task.setChunkCount(chunkCount != null ? chunkCount : 0);
                task.setErrorMessage(null);
                task.setLeaseOwner(null);
                task.setLeaseUntil(null);
                task.setNextRunAt(null);
                task.setCompletedAt(LocalDateTime.now());
                task.setUpdateTime(LocalDateTime.now());
                ingestionTaskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.error("Failed to mark task success: taskId={}", taskId, e);
        }
    }

    private void markTaskFailed(String taskId, String errorMessage) {
        if (taskId == null) return;
        try {
            IngestionTaskEntity task = ingestionTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("FAILED");
                task.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                        ? errorMessage.substring(0, 500) : errorMessage);
                task.setLeaseOwner(null);
                task.setLeaseUntil(null);
                task.setNextRunAt(null);
                task.setCompletedAt(LocalDateTime.now());
                task.setUpdateTime(LocalDateTime.now());
                ingestionTaskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.error("Failed to mark task failed: taskId={}", taskId, e);
        }
    }
}
