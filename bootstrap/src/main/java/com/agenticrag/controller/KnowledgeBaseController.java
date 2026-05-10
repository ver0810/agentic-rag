package com.agenticrag.controller;

import com.agenticrag.infra.ai.storage.FileStorageService;
import com.agenticrag.knowledge.dao.entity.KnowledgeBaseDao;
import com.agenticrag.knowledge.dao.entity.KnowledgeDocumentDao;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.utils.SessionIdGenerator;
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
    private final FileStorageService fileStorageService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   FileStorageService fileStorageService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ResponseEntity<KnowledgeBaseDao> create(@RequestBody KnowledgeBaseDao knowledgeBase,
                                                   @CurrentUser String userId) {
        knowledgeBase.setCreatedBy(userId);
        knowledgeBase.setCollectionName("kb_" + SessionIdGenerator.generate());
        if (knowledgeBase.getEmbeddingModel() == null || knowledgeBase.getEmbeddingModel().isBlank()) {
            knowledgeBase.setEmbeddingModel("text-embedding-3-small");
        }
        return ResponseEntity.ok(knowledgeBaseService.create(knowledgeBase));
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeBaseDao>> list() {
        return ResponseEntity.ok(knowledgeBaseService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBaseDao> getById(@PathVariable String id) {
        return ResponseEntity.ok(knowledgeBaseService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        knowledgeBaseService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{kbId}/documents")
    public ResponseEntity<KnowledgeDocumentDao> uploadDocument(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @CurrentUser String userId) throws IOException {
        
        String fileName = file.getOriginalFilename();
        String fileType = getFileExtension(fileName);
        long fileSize = file.getSize();
        
        String storagePath = "knowledge/" + kbId + "/" + SessionIdGenerator.generate() + "." + fileType;
        fileStorageService.store(file.getInputStream(), storagePath, file.getContentType());
        
        return ResponseEntity.ok(knowledgeBaseService.uploadDocument(kbId, fileName, fileType, fileSize, storagePath, userId));
    }

    @GetMapping("/{kbId}/documents")
    public ResponseEntity<List<KnowledgeDocumentDao>> listDocuments(@PathVariable String kbId) {
        return ResponseEntity.ok(knowledgeBaseService.listDocuments(kbId));
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String docId) {
        knowledgeBaseService.deleteDocument(docId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/documents/{docId}/process")
    public ResponseEntity<Map<String, String>> processDocument(@PathVariable String docId) {
        knowledgeBaseService.processDocument(docId);
        return ResponseEntity.ok(Map.of("message", "Document processing started"));
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
