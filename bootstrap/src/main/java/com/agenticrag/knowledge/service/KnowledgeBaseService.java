package com.agenticrag.knowledge.service;

import com.agenticrag.knowledge.dao.entity.KnowledgeBaseDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentDao;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseDao create(KnowledgeBaseDao knowledgeBase);

    KnowledgeBaseDao getById(String id, String userId);

    List<KnowledgeBaseDao> list(String userId);

    void delete(String id, String userId);

    KnowledgeDocumentDao uploadDocument(String kbId, String fileName, String fileType, long fileSize, String fileUrl, String userId);

    List<KnowledgeDocumentDao> listDocuments(String kbId, String userId);

    void deleteDocument(String docId, String userId);

    void enqueueProcessDocument(String docId, String userId);

    void processDocument(String docId);
}
