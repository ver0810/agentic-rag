package com.agenticrag.infra.ai.api.chat;

import reactor.core.publisher.Flux;

public interface AiChatFacade {

    ChatResponse chat(ChatRequest request);

    Flux<String> stream(ChatRequest request);
}
