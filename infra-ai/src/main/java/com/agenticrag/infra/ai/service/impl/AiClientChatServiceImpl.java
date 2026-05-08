package com.agenticrag.infra.ai.service.impl;

import com.agenticrag.infra.ai.service.AiClientChatService;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

public class AiClientChatServiceImpl implements AiClientChatService {

    private final ChatClient chatClient;

    public AiClientChatServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }


    @Override
    public String call(String message) {
        return "";
    }

    @Override
    public Flux<String> stream(String prompts, String message) {
        return chatClient
                .prompt(prompts)
                .user(message)
                .stream()
                .content();
    }
}
