package com.agenticrag.rag.api;

import com.agenticrag.infra.ai.model.AiRuntimeContext;

public record RagQueryRequest(
        String query,
        String knowledgeBaseId,
        String userId,
        AiRuntimeContext runtimeContext,
        Integer topK,
        String conversationId
) {}
