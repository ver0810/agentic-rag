package com.agenticrag.infra.ai.config;


import com.agenticrag.infra.ai.memory.DatabaseChatMemory;
import com.agenticrag.infra.ai.port.memory.ConversationMemoryPort;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(ConversationMemoryPort repository) {

        return new DatabaseChatMemory(repository, 3);
    }
}
