package com.agenticrag.knowledge.controller;

import com.agenticrag.infra.ai.port.storage.DocumentStoragePort;
import com.agenticrag.knowledge.dao.entity.KnowledgeBaseEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.agenticrag.knowledge.dto.DocumentStructurePreviewDTO;
import com.agenticrag.knowledge.dto.IngestionTaskDTO;
import com.agenticrag.knowledge.service.IngestionTaskService;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.common.SessionIdGenerator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentStoragePort documentStoragePort;
    private final IngestionTaskService ingestionTaskService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   DocumentStoragePort documentStoragePort,
                                   IngestionTaskService ingestionTaskService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentStoragePort = documentStoragePort;
        this.ingestionTaskService = ingestionTaskService;
    }

    @PostMapping
    public ResponseEntity<KnowledgeBaseEntity> create(@RequestBody KnowledgeBaseEntity knowledgeBase,
                                                   @CurrentUser String userId) {
        knowledgeBase.setCreatedBy(userId);
        knowledgeBase.setCollectionName("kb_" + SessionIdGenerator.generate());
        if (knowledgeBase.getEmbeddingModel() == null || knowledgeBase.getEmbeddingModel().isBlank()) {
            knowledgeBase.setEmbeddingModel("text-embedding-3-small");
        }
        return ResponseEntity.ok(knowledgeBaseService.create(knowledgeBase));
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeBaseEntity>> list(@CurrentUser String userId) {
        return ResponseEntity.ok(knowledgeBaseService.list(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBaseEntity> getById(@PathVariable String id,
                                                    @CurrentUser String userId) {
        return ResponseEntity.ok(knowledgeBaseService.getById(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @CurrentUser String userId) {
        knowledgeBaseService.delete(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{kbId}/documents")
    public ResponseEntity<KnowledgeDocumentEntity> uploadDocument(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @CurrentUser String userId) throws IOException {

        String fileName = file.getOriginalFilename();
        String fileType = getFileExtension(fileName);
        long fileSize = file.getSize();

        String storagePath = "knowledge/" + kbId + "/" + SessionIdGenerator.generate() + "." + fileType;
        documentStoragePort.store(file.getInputStream(), storagePath, file.getContentType());

        return ResponseEntity.ok(knowledgeBaseService.uploadDocument(kbId, fileName, fileType, fileSize, storagePath, userId));
    }

    @GetMapping("/{kbId}/documents")
    public ResponseEntity<List<KnowledgeDocumentEntity>> listDocuments(@PathVariable String kbId,
                                                                    @CurrentUser String userId) {
        return ResponseEntity.ok(knowledgeBaseService.listDocuments(kbId, userId));
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String docId,
                                               @CurrentUser String userId) {
        knowledgeBaseService.deleteDocument(docId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/documents/{docId}/process")
    public ResponseEntity<Map<String, String>> processDocument(@PathVariable String docId,
                                                               @CurrentUser String userId) {
        String taskId = knowledgeBaseService.enqueueProcessDocument(docId, userId);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Document processing queued",
                "taskId", taskId));
    }

    @GetMapping("/documents/{docId}/tasks")
    public ResponseEntity<List<IngestionTaskDTO>> listDocumentTasks(@PathVariable String docId,
                                                                    @CurrentUser String userId) {
        return ResponseEntity.ok(ingestionTaskService.listDocumentTasks(docId, userId));
    }

    @GetMapping("/documents/{docId}/structure-preview")
    public ResponseEntity<DocumentStructurePreviewDTO> previewDocumentStructure(
            @PathVariable String docId,
            @RequestParam(value = "strategy", required = false) String strategy,
            @RequestParam(value = "maxSegments", required = false) Integer maxSegments,
            @RequestParam(value = "maxPages", required = false) Integer maxPages,
            @RequestParam(value = "maxChunks", required = false) Integer maxChunks,
            @CurrentUser String userId) {
        return ResponseEntity.ok(knowledgeBaseService.previewDocumentStructure(
                docId,
                userId,
                strategy,
                maxSegments,
                maxPages,
                maxChunks));
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
