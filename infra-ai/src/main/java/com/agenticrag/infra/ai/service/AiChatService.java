package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import reactor.core.publisher.Flux;

public interface AiChatService {

    String call(AiChatScene scene, String message, AiRuntimeOptions runtimeOptions);

    Flux<String> stream(AiChatScene scene, String message, AiRuntimeOptions runtimeOptions);
}
