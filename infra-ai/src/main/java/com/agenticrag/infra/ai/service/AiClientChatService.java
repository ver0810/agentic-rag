package com.agenticrag.infra.ai.service;

import reactor.core.publisher.Flux;

public interface AiClientChatService {

    String call(String message);
    Flux<String> stream(String prompts, String message);
}
