package com.agenticrag.knowledge.service.impl;

import com.agenticrag.common.ApiException;
import com.agenticrag.knowledge.service.IngestionTaskService;
import com.agenticrag.infra.ai.config.EmbeddingProperties;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.infra.ai.port.embedding.KnowledgeEmbeddingPort;
import com.agenticrag.infra.ai.port.storage.DocumentStoragePort;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.agenticrag.rag.parser.DocumentParserFactory;
import com.agenticrag.rag.parser.StructuredParseResult;
import com.agenticrag.knowledge.dao.entity.KnowledgeBaseEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeChunkEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentChunkLogEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.agenticrag.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.agenticrag.knowledge.service.DocumentChunkingService;
import com.agenticrag.knowledge.service.ChunkResult;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.agenticrag.knowledge.dto.DocumentChunkPreviewDTO;
import com.agenticrag.knowledge.dto.DocumentStructurePreviewDTO;
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

    private static final int DEFAULT_PREVIEW_MAX_SEGMENTS = 80;
    private static final int DEFAULT_PREVIEW_MAX_PAGES = 10;
    private static final int DEFAULT_PREVIEW_MAX_CHUNKS = 20;
    private static final int MAX_PREVIEW_CONTENT_CHARS = 600;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final DocumentStoragePort documentStoragePort;
    private final DocumentParserFactory documentParserFactory;
    private final KnowledgeEmbeddingPort knowledgeEmbeddingPort;
    private final VectorIndexPort vectorIndexPort;
    private final EmbeddingProperties embeddingProperties;
    private final DocumentChunkingService documentChunkingService;
    private final IngestionTaskService ingestionTaskService;
    private final TokenCostEstimator tokenCostEstimator;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    KnowledgeDocumentChunkLogMapper knowledgeDocumentChunkLogMapper,
                                    KnowledgeChunkMapper knowledgeChunkMapper,
                                    DocumentStoragePort documentStoragePort,
                                    DocumentParserFactory documentParserFactory,
                                    KnowledgeEmbeddingPort knowledgeEmbeddingPort,
                                    VectorIndexPort vectorIndexPort,
                                    EmbeddingProperties embeddingProperties,
                                    DocumentChunkingService documentChunkingService,
                                    IngestionTaskService ingestionTaskService,
                                    TokenCostEstimator tokenCostEstimator) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeDocumentChunkLogMapper = knowledgeDocumentChunkLogMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.documentStoragePort = documentStoragePort;
        this.documentParserFactory = documentParserFactory;
        this.knowledgeEmbeddingPort = knowledgeEmbeddingPort;
        this.vectorIndexPort = vectorIndexPort;
        this.embeddingProperties = embeddingProperties;
        this.documentChunkingService = documentChunkingService;
        this.ingestionTaskService = ingestionTaskService;
        this.tokenCostEstimator = tokenCostEstimator;
    }

    @Override
    @Transactional
    public KnowledgeBaseEntity create(KnowledgeBaseEntity knowledgeBase) {
        knowledgeBase.setCreateTime(LocalDateTime.now());
        knowledgeBase.setUpdateTime(LocalDateTime.now());
        knowledgeBase.setDeleted(0);
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase;
    }

    @Override
    public KnowledgeBaseEntity getById(String id, String userId) {
        return requireKnowledgeBase(id, userId);
    }

    @Override
    public List<KnowledgeBaseEntity> list(String userId) {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getCreatedBy, userId)
                        .eq(KnowledgeBaseEntity::getDeleted, 0)
                        .orderByDesc(KnowledgeBaseEntity::getCreateTime));
    }

    @Override
    @Transactional
    public void delete(String id, String userId) {
        KnowledgeBaseEntity kb = requireKnowledgeBase(id, userId);
        kb.setDeleted(1);
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);

        knowledgeDocumentMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKbId, id)
                        .set(KnowledgeDocumentEntity::getDeleted, 1));
        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getKbId, id));
        vectorIndexPort.deleteByKbId(id);
    }

    @Override
    @Transactional
    public KnowledgeDocumentEntity uploadDocument(String kbId, String fileName, String fileType, long fileSize, String fileUrl, String userId) {
        requireKnowledgeBase(kbId, userId);
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setKbId(kbId);
        document.setDocName(fileName);
        document.setFileType(fileType);
        document.setFileSize(fileSize);
        document.setFileUrl(fileUrl);
        document.setEnabled(1);
        document.setStatus("pending");
        document.setProcessMode("chunk");
        document.setChunkStrategy("paragraph");
        document.setChunkConfig(null);
        document.setChunkCount(0);
        document.setCreatedBy(userId);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        document.setDeleted(0);
        knowledgeDocumentMapper.insert(document);
        return document;
    }

    @Override
    public List<KnowledgeDocumentEntity> listDocuments(String kbId, String userId) {
        requireKnowledgeBase(kbId, userId);
        return knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKbId, kbId)
                        .eq(KnowledgeDocumentEntity::getCreatedBy, userId)
                        .eq(KnowledgeDocumentEntity::getDeleted, 0)
                        .orderByDesc(KnowledgeDocumentEntity::getCreateTime));
    }

    @Override
    @Transactional
    public void deleteDocument(String docId, String userId) {
        KnowledgeDocumentEntity doc = requireDocument(docId, userId);
        doc.setDeleted(1);
        doc.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);

        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocId, docId));
        vectorIndexPort.deleteByDocId(docId);
        documentStoragePort.delete(doc.getFileUrl());
    }

    @Override
    @Transactional
    public String enqueueProcessDocument(String docId, String userId) {
        KnowledgeDocumentEntity doc = requireDocument(docId, userId);
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
        KnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectById(docId);
        if (doc == null) {
            throw ApiException.notFound("knowledge_document_not_found", "Document not found: " + docId);
        }

        doc.setStatus("running");
        doc.setUpdateTime(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);

        KnowledgeDocumentChunkLogEntity processLog = createProcessLog(doc);
        long totalStart = System.nanoTime();
        try {
            log.info("Starting document processing: docId={}, fileUrl={}, fileType={}", docId, doc.getFileUrl(), doc.getFileType());

            long extractStart = System.nanoTime();
            StructuredParseResult parseResult;
            try (InputStream fileStream = documentStoragePort.load(doc.getFileUrl())) {
                log.info("File loaded successfully");
                var parser = documentParserFactory.getParser(doc.getFileType(), doc.getChunkStrategy());
                log.info("Parser found: {}", parser.getClass().getSimpleName());
                parseResult = parser.parseStructured(fileStream, doc.getFileType(), doc.getChunkStrategy());
            }
            processLog.setExtractDuration(toMillis(extractStart));
            log.info("Document parsed, segment count: {}, content length: {}",
                    parseResult.segments().size(), parseResult.asPlainText().length());

            long chunkStart = System.nanoTime();
            List<ChunkResult> chunkResults = documentChunkingService.chunkWithMetadata(parseResult, doc.getChunkStrategy(), doc.getChunkConfig());
            List<String> chunks = chunkResults.stream().map(ChunkResult::content).toList();
            processLog.setChunkDuration(toMillis(chunkStart));
            processLog.setChunkCount(chunks.size());
            log.info("Text split into {} chunks", chunks.size());

            if (chunks.isEmpty()) {
                throw ApiException.badRequest("document_no_content", "Document did not produce any chunks");
            }

            long embedStart = System.nanoTime();
            List<float[]> embeddings = knowledgeEmbeddingPort.embedAll(new ArrayList<>(chunks));
            processLog.setEmbedDuration(toMillis(embedStart));
            log.info("Embedding completed: {} chunks", embeddings.size());

            long persistStart = System.nanoTime();
            knowledgeChunkMapper.delete(
                    new LambdaQueryWrapper<KnowledgeChunkEntity>()
                            .eq(KnowledgeChunkEntity::getDocId, docId));
            vectorIndexPort.deleteByDocId(docId);

            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunkEntity chunkDao = new KnowledgeChunkEntity();
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

                vectorIndexPort.store(
                        chunkDao.getId(),
                        chunkDao.getContent(),
                        embeddings.get(i),
                        buildVectorMetadata(doc, chunkDao, embeddings.get(i).length, chunkResults.get(i)));
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
        KnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectById(docId);
        return doc == null ? 0 : doc.getChunkCount();
    }

    @Override
    public DocumentStructurePreviewDTO previewDocumentStructure(String docId,
                                                                String userId,
                                                                String strategy,
                                                                Integer maxSegments,
                                                                Integer maxPages,
                                                                Integer maxChunks) {
        KnowledgeDocumentEntity doc = requireDocument(docId, userId);
        String effectiveStrategy = StringUtils.hasText(strategy) ? strategy.trim() : doc.getChunkStrategy();
        if (!StringUtils.hasText(effectiveStrategy)) {
            effectiveStrategy = "paragraph";
        }

        StructuredParseResult parseResult;
        try (InputStream fileStream = documentStoragePort.load(doc.getFileUrl())) {
            var parser = documentParserFactory.getParser(doc.getFileType(), effectiveStrategy);
            parseResult = parser.parseStructured(fileStream, doc.getFileType(), effectiveStrategy);
        } catch (Exception e) {
            throw new RuntimeException("Document structure preview failed: " + e.getMessage(), e);
        }

        List<ChunkResult> chunkResults = documentChunkingService.chunkWithMetadata(
                parseResult,
                effectiveStrategy,
                doc.getChunkConfig());

        int safeMaxSegments = sanitizePreviewSize(maxSegments, DEFAULT_PREVIEW_MAX_SEGMENTS, 200);
        int safeMaxPages = sanitizePreviewSize(maxPages, DEFAULT_PREVIEW_MAX_PAGES, 50);
        int safeMaxChunks = sanitizePreviewSize(maxChunks, DEFAULT_PREVIEW_MAX_CHUNKS, 100);

        Map<String, Object> metadata = new LinkedHashMap<>(parseResult.documentMetadata() == null ? Map.of() : parseResult.documentMetadata());
        metadata.put("previewTotalSegments", parseResult.segments().size());
        metadata.put("previewTotalPages", parseResult.pages().size());
        metadata.put("previewTotalChunks", chunkResults.size());

        List<DocumentChunkPreviewDTO> chunkPreviews = new ArrayList<>();
        for (int i = 0; i < Math.min(chunkResults.size(), safeMaxChunks); i++) {
            ChunkResult chunk = chunkResults.get(i);
            chunkPreviews.add(new DocumentChunkPreviewDTO(
                    i,
                    abbreviatePreviewContent(chunk.content()),
                    chunk.headingPath(),
                    chunk.metadata() == null ? Map.of() : chunk.metadata()));
        }

        return new DocumentStructurePreviewDTO(
                doc.getId(),
                doc.getDocName(),
                doc.getFileType(),
                effectiveStrategy,
                doc.getChunkStrategy(),
                metadata,
                parseResult.pages().stream().limit(safeMaxPages).toList(),
                parseResult.segments().stream().limit(safeMaxSegments).toList(),
                chunkPreviews);
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

    private Map<String, Object> buildVectorMetadata(KnowledgeDocumentEntity doc,
                                                    KnowledgeChunkEntity chunkDao,
                                                    int dimension,
                                                    ChunkResult chunkResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kbId", doc.getKbId());
        metadata.put("docId", doc.getId());
        metadata.put("chunkId", chunkDao.getId());
        metadata.put("chunkIndex", chunkDao.getChunkIndex());
        metadata.put("docName", doc.getDocName());
        metadata.put("docType", doc.getFileType());
        metadata.put("chunkStrategy", doc.getChunkStrategy());
        metadata.put("createdBy", doc.getCreatedBy());
        metadata.put("embeddingModel", embeddingProperties.getModel());
        metadata.put("embeddingDimension", dimension);
        if (chunkResult.headingPath() != null) {
            metadata.put("headingPath", chunkResult.headingPath());
        }
        if (chunkResult.metadata() != null && !chunkResult.metadata().isEmpty()) {
            metadata.putAll(chunkResult.metadata());
        }
        return metadata;
    }

    private KnowledgeBaseEntity requireKnowledgeBase(String kbId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "用户未登录");
        }
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getId, kbId)
                        .eq(KnowledgeBaseEntity::getCreatedBy, userId)
                        .eq(KnowledgeBaseEntity::getDeleted, 0)
                        .last("limit 1"));
        if (kb == null) {
            throw ApiException.notFound("knowledge_base_not_found", "知识库不存在或无权访问");
        }
        return kb;
    }

    private KnowledgeDocumentEntity requireDocument(String docId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "用户未登录");
        }
        KnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getId, docId)
                        .eq(KnowledgeDocumentEntity::getCreatedBy, userId)
                        .eq(KnowledgeDocumentEntity::getDeleted, 0)
                        .last("limit 1"));
        if (doc == null) {
            throw ApiException.notFound("knowledge_document_not_found", "知识文档不存在或无权访问");
        }
        return doc;
    }

    private KnowledgeDocumentChunkLogEntity createProcessLog(KnowledgeDocumentEntity doc) {
        KnowledgeDocumentChunkLogEntity logDao = new KnowledgeDocumentChunkLogEntity();
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

    private void completeProcessLog(KnowledgeDocumentChunkLogEntity logDao, String status, String errorMessage, long totalStart) {
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

    private int sanitizePreviewSize(Integer requested, int defaultValue, int hardLimit) {
        if (requested == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(requested, hardLimit));
    }

    private String abbreviatePreviewContent(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        return content.length() > MAX_PREVIEW_CONTENT_CHARS
                ? content.substring(0, MAX_PREVIEW_CONTENT_CHARS) + "..."
                : content;
    }
}
