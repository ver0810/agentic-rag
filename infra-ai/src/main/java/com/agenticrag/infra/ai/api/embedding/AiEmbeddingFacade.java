package com.agenticrag.infra.ai.api.embedding;

public interface AiEmbeddingFacade {

    EmbeddingResponse embed(EmbeddingRequest request);
}
