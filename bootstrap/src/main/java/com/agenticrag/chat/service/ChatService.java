package com.agenticrag.chat.service;

import com.agenticrag.user.dao.entity.ConversationEntity;
import com.agenticrag.user.dao.entity.MessageEntity;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {

    List<ConversationEntity> listSessions(String userId);

    List<MessageEntity> listMessages(String conversationId, String userId);

    void renameSession(String conversationId, String title, String userId);

    String createSession(String userId);

    void deleteSession(String sessionId, String userId);

    String chat(String message, String scene, String kbId, String userId, String conversationId);

    Flux<String> stream(String message, String scene, String kbId, String userId, String conversationId);
}
