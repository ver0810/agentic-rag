package com.agenticrag.knowledge.service;

import com.agenticrag.knowledge.dao.entity.KnowledgeBaseEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.agenticrag.knowledge.dto.DocumentStructurePreviewDTO;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseEntity create(KnowledgeBaseEntity knowledgeBase);

    KnowledgeBaseEntity getById(String id, String userId);

    List<KnowledgeBaseEntity> list(String userId);

    void delete(String id, String userId);

    KnowledgeDocumentEntity uploadDocument(String kbId, String fileName, String fileType, long fileSize, String fileUrl, String userId);

    List<KnowledgeDocumentEntity> listDocuments(String kbId, String userId);

    void deleteDocument(String docId, String userId);

    String enqueueProcessDocument(String docId, String userId);

    void processDocument(String docId);

    Integer getDocumentChunkCount(String docId);

    DocumentStructurePreviewDTO previewDocumentStructure(String docId,
                                                         String userId,
                                                         String strategy,
                                                         Integer maxSegments,
                                                         Integer maxPages,
                                                         Integer maxChunks);
}
