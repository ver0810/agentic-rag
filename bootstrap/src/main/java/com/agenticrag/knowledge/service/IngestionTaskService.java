package com.agenticrag.knowledge.service;

import com.agenticrag.knowledge.dao.entity.IngestionTaskEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.agenticrag.knowledge.dto.IngestionTaskDTO;

import java.util.List;

public interface IngestionTaskService {

    String enqueueDocumentTask(KnowledgeDocumentEntity document, String userId);

    IngestionTaskEntity claimNextQueuedTask(String workerId);

    void markRunning(IngestionTaskEntity task, String workerId);

    void markSuccess(IngestionTaskEntity task, Integer chunkCount);

    void markFailed(IngestionTaskEntity task, String errorMessage);

    void renewLease(String taskId, String workerId);

    String startTaskNode(IngestionTaskEntity task, String nodeType, int nodeOrder, String message);

    void completeTaskNode(String taskId, String nodeId, String message, String outputJson, long durationMs);

    void failTaskNode(String taskId, String nodeId, String errorMessage, String outputJson, long durationMs);

    List<IngestionTaskDTO> listDocumentTasks(String docId, String userId);

    IngestionTaskDTO getTask(String taskId, String userId);

    String retryTask(String taskId, String userId);
}
