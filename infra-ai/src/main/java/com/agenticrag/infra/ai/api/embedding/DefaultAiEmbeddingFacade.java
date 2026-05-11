package com.agenticrag.infra.ai.api.embedding;

import com.agenticrag.infra.ai.service.AiEmbeddingService;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiEmbeddingFacade implements AiEmbeddingFacade {

    private final AiEmbeddingService aiEmbeddingService;

    public DefaultAiEmbeddingFacade(AiEmbeddingService aiEmbeddingService) {
        this.aiEmbeddingService = aiEmbeddingService;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        return new EmbeddingResponse(aiEmbeddingService.embed(request.text(), request.runtimeContext()));
    }
}
