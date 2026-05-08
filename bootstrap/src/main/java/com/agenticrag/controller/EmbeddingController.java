package com.agenticrag.controller;

import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.service.AiEmbeddingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import com.agenticrag.user.service.UserAiProviderConfigService;

@RestController
@RequestMapping("/embedding")
public class EmbeddingController {

    private final AiEmbeddingService aiEmbeddingService;
    private final UserAiProviderConfigService userAiProviderConfigService;

    public EmbeddingController(AiEmbeddingService aiEmbeddingService,
                               UserAiProviderConfigService userAiProviderConfigService) {
        this.aiEmbeddingService = aiEmbeddingService;
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    @PostMapping
    public float[] embed(@RequestParam(name = "text") String text,
                         @RequestHeader(name = "X-User-Id", required = false) String userId) {
        AiRuntimeOptions runtimeOptions = userAiProviderConfigService.resolveRuntimeOptions(userId);
        return aiEmbeddingService.embed(text, runtimeOptions);
    }
}
