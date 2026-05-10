package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import reactor.core.publisher.Flux;

public interface AiChatService {

    String call(AiChatScene scene, String message, AiRuntimeContext context, String conversationId, String userId, String kbId);

    Flux<String> stream(AiChatScene scene, String message, AiRuntimeContext context, String conversationId, String userId);

    Flux<String> stream(AiChatScene scene, String message, AiRuntimeContext context, String conversationId, String userId, String kbId);
}
