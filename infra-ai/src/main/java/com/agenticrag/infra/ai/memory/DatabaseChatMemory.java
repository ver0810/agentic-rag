package com.agenticrag.infra.ai.memory;

import com.agenticrag.infra.ai.port.memory.ConversationMemoryPort;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.stream.Collectors;

public class DatabaseChatMemory implements ChatMemory {


    private final ConversationMemoryPort conversationMemoryPort;
    private final int maxMessages;

    public DatabaseChatMemory(ConversationMemoryPort conversationMemoryPort, int maxMessages) {
        this.conversationMemoryPort = conversationMemoryPort;
        this.maxMessages = maxMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {

    }

    @Override
    public List<Message> get(String conversationId) {
        return conversationMemoryPort.getRecentMessages(conversationId, maxMessages)
                .stream()
                .map(this::convertToMessage)
                .collect(Collectors.toList());
    }

    private Message convertToMessage(ConversationMemoryPort.MemoryMessage chatMessage) {

        return switch (chatMessage.role().toLowerCase()) {
            case "user" -> new UserMessage(chatMessage.content());
            case "assistant" -> new AssistantMessage(chatMessage.content());
            case "system" -> new SystemMessage(chatMessage.content());
            default -> new UserMessage(chatMessage.content());
        };
    }

    @Override
    public void clear(String conversationId) {

    }
}
