package com.agenticrag.ingestion.service;

import com.agenticrag.ingestion.dao.entity.IngestionTaskDao;
import com.agenticrag.ingestion.dto.IngestionTaskDTO;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentDao;

import java.util.List;

public interface IngestionTaskService {

    String enqueueDocumentTask(KnowledgeDocumentDao document, String userId);

    IngestionTaskDao claimNextQueuedTask(String workerId);

    void markRunning(IngestionTaskDao task, String workerId);

    void markSuccess(IngestionTaskDao task, Integer chunkCount);

    void markFailed(IngestionTaskDao task, String errorMessage);

    void renewLease(String taskId, String workerId);

    String startTaskNode(IngestionTaskDao task, String nodeType, int nodeOrder, String message);

    void completeTaskNode(String taskId, String nodeId, String message, String outputJson, long durationMs);

    void failTaskNode(String taskId, String nodeId, String errorMessage, String outputJson, long durationMs);

    List<IngestionTaskDTO> listDocumentTasks(String docId, String userId);

    IngestionTaskDTO getTask(String taskId, String userId);

    String retryTask(String taskId, String userId);
}
