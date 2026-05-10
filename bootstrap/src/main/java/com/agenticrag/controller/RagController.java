package com.agenticrag.controller;

import com.agenticrag.infra.ai.rag.query.RagQueryService;
import com.agenticrag.user.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagQueryService ragQueryService;

    public RagController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, String>> query(@RequestBody RagQueryRequest request,
                                                     @CurrentUser String userId) {
        String answer = ragQueryService.query(
                request.query(),
                request.kbId(),
                userId,
                request.topK() != null ? request.topK() : 5);
        return ResponseEntity.ok(Map.of("answer", answer));
    }

    public record RagQueryRequest(String query, String kbId, Integer topK) {}
}
