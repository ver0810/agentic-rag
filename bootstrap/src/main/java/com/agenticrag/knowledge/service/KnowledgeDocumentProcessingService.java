package com.agenticrag.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KnowledgeDocumentProcessingService {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeDocumentProcessingService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Async("knowledgeProcessingExecutor")
    public void processAsync(String docId) {
        try {
            knowledgeBaseService.processDocument(docId);
        } catch (Exception ex) {
            log.error("Asynchronous knowledge document processing failed: docId={}", docId, ex);
        }
    }
}
