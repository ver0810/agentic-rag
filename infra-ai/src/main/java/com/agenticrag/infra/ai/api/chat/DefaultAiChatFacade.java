package com.agenticrag.infra.ai.api.chat;

import com.agenticrag.infra.ai.service.AiChatService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class DefaultAiChatFacade implements AiChatFacade {

    private final AiChatService aiChatService;

    public DefaultAiChatFacade(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return new ChatResponse(aiChatService.call(
                request.scene(),
                request.message(),
                request.runtimeContext(),
                request.conversationId(),
                request.userId()));
    }

    @Override
    public Flux<String> stream(ChatRequest request) {
        return aiChatService.stream(
                request.scene(),
                request.message(),
                request.runtimeContext(),
                request.conversationId(),
                request.userId());
    }
}
