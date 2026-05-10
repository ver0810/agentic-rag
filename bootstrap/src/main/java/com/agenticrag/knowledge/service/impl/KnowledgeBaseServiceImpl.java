package com.agenticrag.knowledge.service.impl;

import com.agenticrag.common.ApiException;
import com.agenticrag.ingestion.service.IngestionTaskService;
import com.agenticrag.infra.ai.config.EmbeddingProperties;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.infra.ai.rag.parser.DocumentParserFactory;
import com.agenticrag.infra.ai.rag.vector.VectorStore;
import com.agenticrag.infra.ai.service.KnowledgeEmbeddingService;
import com.agenticrag.infra.ai.storage.FileStorageService;
import com.agenticrag.knowledge.dao.entity.KnowledgeBaseDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeChunkDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentChunkLogDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentDao;
import com.agenticrag.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.agenticrag.knowledge.service.DocumentChunkingService;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final FileStorageService fileStorageService;
    private final DocumentParserFactory documentParserFactory;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final VectorStore vectorStore;
    private final EmbeddingProperties embeddingProperties;
    private final DocumentChunkingService documentChunkingService;
    private final IngestionTaskService ingestionTaskService;
    private final TokenCostEstimator tokenCostEstimator;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper,
                                    KnowledgeChunkMapper knowledgeChunkMapper,
                                    FileStorageService fileStorageService,
                                    DocumentParserFactory documentParserFactory,
                                    KnowledgeEmbeddingService knowledgeEmbeddingService,
                                    VectorStore vectorStore,
                                    EmbeddingProperties embeddingProperties,
                                    DocumentChunkingService documentChunkingService,
                                    IngestionTaskService ingestionTaskService,
                                    TokenCostEstimator tokenCostEstimator) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeDocumentChunkLogMapper = knowledgeDocumentChunkLogMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.fileStorageService = fileStorageService;
        this.documentParserFactory = documentParserFactory;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.vectorStore = vectorStore;
        this.embeddingProperties = embeddingProperties;
        this.documentChunkingService = documentChunkingService;
        this.ingestionTaskService = ingestionTaskService;
        this.tokenCostEstimator = tokenCostEstimator;
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
    public KnowledgeBaseDao getById(String id, String userId) {
        return requireKnowledgeBase(id, userId);
    }

    @Override
    public List<KnowledgeBaseDao> list(String userId) {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBaseDao>()
                        .eq(KnowledgeBaseDao::getCreatedBy, userId)
                        .eq(KnowledgeBaseDao::getDeleted, 0)
                        .orderByDesc(KnowledgeBaseDao::getCreateTime));
    }

    @Override
    @Transactional
    public void delete(String id, String userId) {
        KnowledgeBaseDao kb = requireKnowledgeBase(id, userId);
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

    @Override
    @Transactional
    public KnowledgeDocumentDao uploadDocument(String kbId, String fileName, String fileType, long fileSize, String fileUrl, String userId) {
        requireKnowledgeBase(kbId, userId);
        KnowledgeDocumentDao document = new KnowledgeDocumentDao();
        document.setKbId(kbId);
        document.setDocName(fileName);
        document.setFileType(fileType);
        document.setFileSize(fileSize);
        document.setFileUrl(fileUrl);
        document.setEnabled(1);
        document.setStatus("pending");
        document.setProcessMode("chunk");
        document.setChunkStrategy("paragraph");
        document.setChunkConfig("{\"maxChars\":900,\"overlapParagraphs\":1,\"minChunkChars\":180}");
        document.setChunkCount(0);
        document.setCreatedBy(userId);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        document.setDeleted(0);
        knowledgeDocumentMapper.insert(document);
        return document;
    }

    @Override
    public List<KnowledgeDocumentDao> listDocuments(String kbId, String userId) {
        requireKnowledgeBase(kbId, userId);
        return knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentDao>()
                        .eq(KnowledgeDocumentDao::getKbId, kbId)
                        .eq(KnowledgeDocumentDao::getCreatedBy, userId)
                        .eq(KnowledgeDocumentDao::getDeleted, 0)
                        .orderByDesc(KnowledgeDocumentDao::getCreateTime));
    }

    @Override
    @Transactional
    public void deleteDocument(String docId, String userId) {
        KnowledgeDocumentDao doc = requireDocument(docId, userId);
        doc.setDeleted(1);
        doc.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);

        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunkDao>()
                        .eq(KnowledgeChunkDao::getDocId, docId));
        vectorStore.deleteByDocId(docId);
        fileStorageService.delete(doc.getFileUrl());
    }

    @Override
    @Transactional
    public String enqueueProcessDocument(String docId, String userId) {
        KnowledgeDocumentDao doc = requireDocument(docId, userId);
        String status = normalizeStatus(doc.getStatus());
        if ("queued".equals(status) || "running".equals(status)) {
            throw ApiException.badRequest("document_processing_in_progress", "文档正在处理中，请稍后再试");
        }
        doc.setStatus("queued");
        doc.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);
        return ingestionTaskService.enqueueDocumentTask(doc, userId);
    }

    @Override
    @Transactional
    public void processDocument(String docId) {
        KnowledgeDocumentDao doc = knowledgeDocumentMapper.selectById(docId);
        if (doc == null) {
            throw ApiException.notFound("knowledge_document_not_found", "Document not found: " + docId);
        }

        doc.setStatus("running");
        doc.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);

        KnowledgeDocumentChunkLogDao processLog = createProcessLog(doc);
        long totalStart = System.nanoTime();
        try {
            log.info("Starting document processing: docId={}, fileUrl={}, fileType={}", docId, doc.getFileUrl(), doc.getFileType());

            long extractStart = System.nanoTime();
            String content;
            try (InputStream fileStream = fileStorageService.load(doc.getFileUrl())) {
                log.info("File loaded successfully");
                var parser = documentParserFactory.getParser(doc.getFileType());
                log.info("Parser found: {}", parser.getClass().getSimpleName());
                content = parser.parse(fileStream, doc.getFileType());
            }
            processLog.setExtractDuration(toMillis(extractStart));
            log.info("Document parsed, content length: {}", content.length());

            long chunkStart = System.nanoTime();
            List<String> chunks = documentChunkingService.chunk(content, doc.getChunkStrategy(), doc.getChunkConfig());
            processLog.setChunkDuration(toMillis(chunkStart));
            processLog.setChunkCount(chunks.size());
            log.info("Text split into {} chunks", chunks.size());

            if (chunks.isEmpty()) {
                throw ApiException.badRequest("document_no_content", "Document did not produce any chunks");
            }

            long embedStart = System.nanoTime();
            List<float[]> embeddings = knowledgeEmbeddingService.embedAll(new ArrayList<>(chunks));
            processLog.setEmbedDuration(toMillis(embedStart));
            log.info("Embedding completed, got {} vectors", embeddings.size());

            if (embeddings.size() != chunks.size()) {
                throw new IllegalStateException("Embedding result size does not match chunk count");
            }

            long persistStart = System.nanoTime();
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
                chunkDao.setTokenCount(tokenCostEstimator.estimateTokens(chunks.get(i)));
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
                        buildVectorMetadata(doc, chunkDao, embeddings.get(i).length));
            }
            processLog.setPersistDuration(toMillis(persistStart));

            doc.setStatus("success");
            doc.setChunkCount(chunks.size());
            doc.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);
            completeProcessLog(processLog, "success", null, totalStart);

            log.info("Document processed successfully: {}, chunks: {}", docId, chunks.size());
        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);
            completeProcessLog(processLog, "failed", abbreviateError(e.getMessage()), totalStart);
            log.error("Failed to process document: {}. Error: {}", docId, e.getMessage(), e);
            throw new RuntimeException("Document processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Integer getDocumentChunkCount(String docId) {
        KnowledgeDocumentDao doc = knowledgeDocumentMapper.selectById(docId);
        return doc == null ? 0 : doc.getChunkCount();
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

    private Map<String, Object> buildVectorMetadata(KnowledgeDocumentDao doc, KnowledgeChunkDao chunkDao, int dimension) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kbId", doc.getKbId());
        metadata.put("docId", doc.getId());
        metadata.put("chunkId", chunkDao.getId());
        metadata.put("chunkIndex", chunkDao.getChunkIndex());
        metadata.put("docName", doc.getDocName());
        metadata.put("createdBy", doc.getCreatedBy());
        metadata.put("embeddingModel", embeddingProperties.getModel());
        metadata.put("embeddingDimension", dimension);
        return metadata;
    }

    private KnowledgeBaseDao requireKnowledgeBase(String kbId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "用户未登录");
        }
        KnowledgeBaseDao kb = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseDao>()
                        .eq(KnowledgeBaseDao::getId, kbId)
                        .eq(KnowledgeBaseDao::getCreatedBy, userId)
                        .eq(KnowledgeBaseDao::getDeleted, 0)
                        .last("limit 1"));
        if (kb == null) {
            throw ApiException.notFound("knowledge_base_not_found", "知识库不存在或无权访问");
        }
        return kb;
    }

    private KnowledgeDocumentDao requireDocument(String docId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "用户未登录");
        }
        KnowledgeDocumentDao doc = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentDao>()
                        .eq(KnowledgeDocumentDao::getId, docId)
                        .eq(KnowledgeDocumentDao::getCreatedBy, userId)
                        .eq(KnowledgeDocumentDao::getDeleted, 0)
                        .last("limit 1"));
        if (doc == null) {
            throw ApiException.notFound("knowledge_document_not_found", "知识文档不存在或无权访问");
        }
        return doc;
    }

    private KnowledgeDocumentChunkLogDao createProcessLog(KnowledgeDocumentDao doc) {
        KnowledgeDocumentChunkLogDao logDao = new KnowledgeDocumentChunkLogDao();
        logDao.setDocId(doc.getId());
        logDao.setStatus("running");
        logDao.setProcessMode(doc.getProcessMode());
        logDao.setChunkStrategy(doc.getChunkStrategy());
        logDao.setPipelineId(doc.getPipelineId());
        logDao.setStartTime(LocalDateTime.now());
        logDao.setCreateTime(LocalDateTime.now());
        logDao.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentChunkLogMapper.insert(logDao);
        return logDao;
    }

    private void completeProcessLog(KnowledgeDocumentChunkLogDao logDao, String status, String errorMessage, long totalStart) {
        logDao.setStatus(status);
        logDao.setErrorMessage(errorMessage);
        logDao.setTotalDuration(toMillis(totalStart));
        logDao.setEndTime(LocalDateTime.now());
        logDao.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentChunkLogMapper.updateById(logDao);
    }

    private long toMillis(long startNanoTime) {
        return (System.nanoTime() - startNanoTime) / 1_000_000;
    }

    private String abbreviateError(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase();
    }
}
