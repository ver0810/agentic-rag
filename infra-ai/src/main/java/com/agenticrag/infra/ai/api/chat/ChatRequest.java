package com.agenticrag.infra.ai.api.chat;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;

public record ChatRequest(
        AiChatScene scene,
        String message,
        AiRuntimeContext runtimeContext,
        String conversationId,
        String userId
) {
}
