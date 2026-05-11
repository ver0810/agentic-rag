package com.agenticrag.chat.controller;

import com.agenticrag.infra.ai.api.rag.RagFacade;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.rag.query.RagQueryResult;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.user.service.UserAiProviderConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagFacade ragFacade;
    private final KnowledgeBaseService knowledgeBaseService;
    private final UserAiProviderConfigService userAiProviderConfigService;

    public RagController(RagFacade ragFacade,
                         KnowledgeBaseService knowledgeBaseService,
                         UserAiProviderConfigService userAiProviderConfigService) {
        this.ragFacade = ragFacade;
        this.knowledgeBaseService = knowledgeBaseService;
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    @PostMapping("/query")
    public ResponseEntity<RagQueryResult> query(@RequestBody RagQueryRequest request,
                                                @CurrentUser String userId) {
        knowledgeBaseService.getById(request.kbId(), userId);
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);
        RagQueryResult result = ragFacade.query(new com.agenticrag.infra.ai.api.rag.RagQueryRequest(
                request.query(),
                request.kbId(),
                userId,
                context,
                request.topK()));
        return ResponseEntity.ok(result);
    }

    public record RagQueryRequest(String query, String kbId, Integer topK) {}
}
