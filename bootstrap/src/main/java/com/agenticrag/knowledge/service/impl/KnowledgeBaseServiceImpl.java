package com.agenticrag.knowledge.service.impl;

import com.agenticrag.infra.ai.rag.parser.DocumentParserFactory;
import com.agenticrag.infra.ai.rag.vector.VectorStore;
import com.agenticrag.infra.ai.config.EmbeddingProperties;
import com.agenticrag.infra.ai.service.KnowledgeEmbeddingService;
import com.agenticrag.infra.ai.storage.FileStorageService;
import com.agenticrag.knowledge.dao.entity.KnowledgeBaseDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeChunkDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentDao;
import com.agenticrag.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final FileStorageService fileStorageService;
    private final DocumentParserFactory documentParserFactory;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final VectorStore vectorStore;
    private final EmbeddingProperties embeddingProperties;

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    KnowledgeChunkMapper knowledgeChunkMapper,
                                    FileStorageService fileStorageService,
                                    DocumentParserFactory documentParserFactory,
                                    KnowledgeEmbeddingService knowledgeEmbeddingService,
                                    VectorStore vectorStore,
                                    EmbeddingProperties embeddingProperties) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.fileStorageService = fileStorageService;
        this.documentParserFactory = documentParserFactory;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.vectorStore = vectorStore;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    @Transactional
    public KnowledgeBaseDao create(KnowledgeBaseDao knowledgeBase) {
        knowledgeBase.setCreateTime(LocalDateTime.now());
        knowledgeBase.setUpdateTime(LocalDateTime.now());
        knowledgeBase.setDeleted(0);
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase;
    }

    @Override
    public KnowledgeBaseDao getById(String id) {
        return knowledgeBaseMapper.selectById(id);
    }

    @Override
    public List<KnowledgeBaseDao> list() {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBaseDao>()
                        .eq(KnowledgeBaseDao::getDeleted, 0)
                        .orderByDesc(KnowledgeBaseDao::getCreateTime));
    }

    @Override
    @Transactional
    public void delete(String id) {
        KnowledgeBaseDao kb = knowledgeBaseMapper.selectById(id);
        if (kb != null) {
            kb.setDeleted(1);
            kb.setUpdateTime(LocalDateTime.now());
            knowledgeBaseMapper.updateById(kb);

            knowledgeDocumentMapper.update(
                    null,
                    new LambdaUpdateWrapper<KnowledgeDocumentDao>()
                            .eq(KnowledgeDocumentDao::getKbId, id)
                            .set(KnowledgeDocumentDao::getDeleted, 1));
            knowledgeChunkMapper.delete(
                    new LambdaQueryWrapper<KnowledgeChunkDao>()
                            .eq(KnowledgeChunkDao::getKbId, id));
            vectorStore.deleteByKbId(id);
        }
    }

    @Override
    @Transactional
    public KnowledgeDocumentDao uploadDocument(String kbId, String fileName, String fileType, long fileSize, String fileUrl, String userId) {
        KnowledgeDocumentDao document = new KnowledgeDocumentDao();
        document.setKbId(kbId);
        document.setDocName(fileName);
        document.setFileType(fileType);
        document.setFileSize(fileSize);
        document.setFileUrl(fileUrl);
        document.setEnabled(1);
        document.setStatus("pending");
        document.setProcessMode("chunk");
        document.setChunkStrategy("fixed");
        document.setChunkCount(0);
        document.setCreatedBy(userId);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        document.setDeleted(0);
        knowledgeDocumentMapper.insert(document);
        return document;
    }

    @Override
    public List<KnowledgeDocumentDao> listDocuments(String kbId) {
        return knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentDao>()
                        .eq(KnowledgeDocumentDao::getKbId, kbId)
                        .eq(KnowledgeDocumentDao::getDeleted, 0)
                        .orderByDesc(KnowledgeDocumentDao::getCreateTime));
    }

    @Override
    @Transactional
    public void deleteDocument(String docId) {
        KnowledgeDocumentDao doc = knowledgeDocumentMapper.selectById(docId);
        if (doc != null) {
            doc.setDeleted(1);
            doc.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);

            knowledgeChunkMapper.delete(
                    new LambdaQueryWrapper<KnowledgeChunkDao>()
                            .eq(KnowledgeChunkDao::getDocId, docId));
            vectorStore.deleteByDocId(docId);

            fileStorageService.delete(doc.getFileUrl());
        }
    }

    @Override
    @Transactional
    public void processDocument(String docId) {
        KnowledgeDocumentDao doc = knowledgeDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + docId);
        }

        doc.setStatus("running");
        doc.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);

        try {
            log.info("Starting document processing: docId={}, fileUrl={}, fileType={}", docId, doc.getFileUrl(), doc.getFileType());
            
            InputStream fileStream = fileStorageService.load(doc.getFileUrl());
            log.info("File loaded successfully");
            
            var parser = documentParserFactory.getParser(doc.getFileType());
            log.info("Parser found: {}", parser.getClass().getSimpleName());
            
            String content = parser.parse(fileStream, doc.getFileType());
            log.info("Document parsed, content length: {}", content.length());

            List<String> chunks = splitText(content, CHUNK_SIZE, CHUNK_OVERLAP);
            log.info("Text split into {} chunks", chunks.size());

            List<String> chunkContents = new ArrayList<>();
            for (String chunk : chunks) {
                chunkContents.add(chunk);
            }

            log.info("Starting embedding for {} chunks...", chunkContents.size());
            List<float[]> embeddings = knowledgeEmbeddingService.embedAll(chunkContents);
            log.info("Embedding completed, got {} vectors", embeddings.size());

            knowledgeChunkMapper.delete(
                    new LambdaQueryWrapper<KnowledgeChunkDao>()
                            .eq(KnowledgeChunkDao::getDocId, docId));
            vectorStore.deleteByDocId(docId);

            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunkDao chunkDao = new KnowledgeChunkDao();
                chunkDao.setKbId(doc.getKbId());
                chunkDao.setDocId(docId);
                chunkDao.setChunkIndex(i);
                chunkDao.setContent(chunks.get(i));
                chunkDao.setContentHash(calculateHash(chunks.get(i)));
                chunkDao.setCharCount(chunks.get(i).length());
                chunkDao.setEnabled(1);
                chunkDao.setCreatedBy(doc.getCreatedBy());
                chunkDao.setCreateTime(LocalDateTime.now());
                chunkDao.setUpdateTime(LocalDateTime.now());
                chunkDao.setDeleted(0);
                knowledgeChunkMapper.insert(chunkDao);

                vectorStore.store(
                        chunkDao.getId(),
                        chunkDao.getContent(),
                        embeddings.get(i),
                        buildVectorMetadata(doc, chunkDao));
            }

            doc.setStatus("success");
            doc.setChunkCount(chunks.size());
            doc.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);

            log.info("Document processed successfully: {}, chunks: {}", docId, chunks.size());
        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);
            log.error("Failed to process document: {}. Error: {}", docId, e.getMessage(), e);
            throw new RuntimeException("Document processing failed: " + e.getMessage(), e);
        }
    }

    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end);
            chunks.add(chunk);
            start += chunkSize - overlap;
        }
        return chunks;
    }

    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildVectorMetadata(KnowledgeDocumentDao doc, KnowledgeChunkDao chunkDao) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kbId", doc.getKbId());
        metadata.put("docId", doc.getId());
        metadata.put("chunkId", chunkDao.getId());
        metadata.put("chunkIndex", chunkDao.getChunkIndex());
        metadata.put("docName", doc.getDocName());
        metadata.put("createdBy", doc.getCreatedBy());
        metadata.put("embeddingModel", embeddingProperties.getModel());
        return metadata;
    }
}
