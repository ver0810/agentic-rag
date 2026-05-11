package com.agenticrag.infra.ai.api.embedding;

import com.agenticrag.infra.ai.model.AiRuntimeContext;

public record EmbeddingRequest(
        String text,
        AiRuntimeContext runtimeContext
) {
}
