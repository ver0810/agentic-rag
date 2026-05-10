package com.agenticrag.infra.ai.memory;

import java.util.List;

public interface ChatMemoryRepository {
    List<ChatMessage> getRecentMessages(String conversationId, int limit);

    record ChatMessage(String role, String content) {};
}
