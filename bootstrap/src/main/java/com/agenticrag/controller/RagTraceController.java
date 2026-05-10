package com.agenticrag.controller;

import com.agenticrag.ragtrace.dto.RagTraceRunDTO;
import com.agenticrag.ragtrace.service.RagTraceService;
import com.agenticrag.user.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rag/traces")
public class RagTraceController {

    private final RagTraceService ragTraceService;

    public RagTraceController(RagTraceService ragTraceService) {
        this.ragTraceService = ragTraceService;
    }

    @GetMapping
    public ResponseEntity<List<RagTraceRunDTO>> list(@CurrentUser String userId,
                                                     @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(ragTraceService.listRuns(userId, limit == null ? 20 : limit));
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<RagTraceRunDTO> detail(@CurrentUser String userId,
                                                 @PathVariable String traceId) {
        return ResponseEntity.ok(ragTraceService.getRun(userId, traceId));
    }
}
