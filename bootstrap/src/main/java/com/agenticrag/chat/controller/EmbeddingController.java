package com.agenticrag.chat.controller;

import com.agenticrag.infra.ai.api.embedding.AiEmbeddingFacade;
import com.agenticrag.infra.ai.api.embedding.EmbeddingRequest;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.user.auth.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.agenticrag.user.service.UserAiProviderConfigService;

@RestController
@RequestMapping("/embedding")
public class EmbeddingController {

    private final AiEmbeddingFacade aiEmbeddingFacade;
    private final UserAiProviderConfigService userAiProviderConfigService;

    public EmbeddingController(AiEmbeddingFacade aiEmbeddingFacade,
                               UserAiProviderConfigService userAiProviderConfigService) {
        this.aiEmbeddingFacade = aiEmbeddingFacade;
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    @PostMapping
    public float[] embed(@RequestParam(name = "text") String text,
                         @CurrentUser String userId) {
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);
        return aiEmbeddingFacade.embed(new EmbeddingRequest(text, context)).vector();
    }
}
