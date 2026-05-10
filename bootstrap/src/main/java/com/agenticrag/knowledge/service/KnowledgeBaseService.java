package com.agenticrag.knowledge.service;

import com.agenticrag.knowledge.dao.entity.KnowledgeBaseDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentDao;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseDao create(KnowledgeBaseDao knowledgeBase);

    KnowledgeBaseDao getById(String id);

    List<KnowledgeBaseDao> list();

    void delete(String id);

    KnowledgeDocumentDao uploadDocument(String kbId, String fileName, String fileType, long fileSize, String fileUrl, String userId);

    List<KnowledgeDocumentDao> listDocuments(String kbId);

    void deleteDocument(String docId);

    void processDocument(String docId);
}
