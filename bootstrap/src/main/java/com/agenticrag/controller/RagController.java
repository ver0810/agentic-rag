package com.agenticrag.controller;

import com.agenticrag.infra.ai.rag.query.RagQueryResult;
import com.agenticrag.infra.ai.rag.query.RagQueryService;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.agenticrag.user.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagQueryService ragQueryService;
    private final KnowledgeBaseService knowledgeBaseService;

    public RagController(RagQueryService ragQueryService,
                         KnowledgeBaseService knowledgeBaseService) {
        this.ragQueryService = ragQueryService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/query")
    public ResponseEntity<RagQueryResult> query(@RequestBody RagQueryRequest request,
                                                @CurrentUser String userId) {
        knowledgeBaseService.getById(request.kbId(), userId);
        RagQueryResult result = ragQueryService.queryDetailed(
                request.query(),
                request.kbId(),
                userId,
                request.topK() != null ? request.topK() : 5);
        return ResponseEntity.ok(result);
    }

    public record RagQueryRequest(String query, String kbId, Integer topK) {}
}
