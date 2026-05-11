package com.agenticrag.infra.ai.port.memory;

import java.util.List;

public interface ConversationMemoryPort {

    List<? extends MemoryMessage> getRecentMessages(String conversationId, int limit);

    interface MemoryMessage {
        String role();

        String content();
    }
}
