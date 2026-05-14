package com.agenticrag.chat.service.impl;

import com.agenticrag.chat.dto.ChatResult;
import com.agenticrag.infra.ai.api.chat.AiChatFacade;
import com.agenticrag.infra.ai.api.chat.ChatRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agenticrag.rag.api.RagFacade;
import com.agenticrag.rag.api.RagQueryRequest;
import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.chat.service.ChatService;
import com.agenticrag.rag.query.RagQueryResult;
import com.agenticrag.user.dao.entity.ConversationEntity;
import com.agenticrag.user.dao.entity.MessageEntity;
import com.agenticrag.user.dao.mapper.ConversationMapper;
import com.agenticrag.user.dao.mapper.MessageMapper;
import com.agenticrag.user.service.UserAiProviderConfigService;
import com.agenticrag.common.SessionIdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ChatServiceImpl implements ChatService {

    private final AiChatFacade aiChatFacade;
    private final RagFacade ragFacade;
    private final UserAiProviderConfigService userAiProviderConfigService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    private final Cache<String, List<MessageEntity>> messageCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    private final Cache<String, List<ConversationEntity>> sessionCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();

    public ChatServiceImpl(AiChatFacade aiChatFacade,
                           RagFacade ragFacade,
                           UserAiProviderConfigService userAiProviderConfigService,
                           ConversationMapper conversationMapper,
                           MessageMapper messageMapper,
                           ObjectMapper objectMapper) {
        this.aiChatFacade = aiChatFacade;
        this.ragFacade = ragFacade;
        this.userAiProviderConfigService = userAiProviderConfigService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ConversationEntity> listSessions(String userId) {
        return sessionCache.get(userId, id ->
                conversationMapper.selectList(
                        new LambdaQueryWrapper<ConversationEntity>()
                                .eq(ConversationEntity::getUserId, id)
                                .eq(ConversationEntity::getDeleted, 0)
                                .orderByDesc(ConversationEntity::getLastTime)
                )
        );
    }

    @Override
    public List<MessageEntity> listMessages(String conversationId, String userId) {
        return messageCache.get(conversationId, id ->
                messageMapper.selectList(
                        new LambdaQueryWrapper<MessageEntity>()
                                .eq(MessageEntity::getConversationId, id)
                                .eq(MessageEntity::getUserId, userId)
                                .eq(MessageEntity::getDeleted, 0)
                                .orderByAsc(MessageEntity::getCreateTime)
                )
        );
    }

    @Override
    public void renameSession(String conversationId, String title, String userId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationEntity>()
                .eq(ConversationEntity::getConversationId, conversationId)
                .eq(ConversationEntity::getUserId, userId)
                .set(ConversationEntity::getTitle, title));
        sessionCache.invalidate(userId);
    }

    @Override
    public String createSession(String userId) {
        String sessionId = SessionIdGenerator.generate();

        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(sessionId);
        conversation.setUserId(userId);
        conversation.setTitle("New Chat");
        conversation.setLastTime(LocalDateTime.now());
        conversationMapper.insert(conversation);

        sessionCache.invalidate(userId);
        return sessionId;
    }

    @Override
    public void deleteSession(String sessionId, String userId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationEntity>()
                .eq(ConversationEntity::getConversationId, sessionId)
                .eq(ConversationEntity::getUserId, userId)
                .set(ConversationEntity::getDeleted, 1));
        sessionCache.invalidate(userId);
        messageCache.invalidate(sessionId);
    }

    @Override
    public String chat(String message, String scene, String kbId, String userId, String conversationId) {
        saveMessage(conversationId, userId, "user", message, serializeMetadata(buildUserMessageMetadata(scene, kbId)));
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);
        AiChatScene chatScene = AiChatScene.fromCode(scene);
        if (isRagRequest(chatScene, kbId)) {
            RagQueryResult result = ragFacade.query(new RagQueryRequest(message, kbId, userId, context, 5));
            saveMessage(conversationId, userId, "assistant", result.answer(), serializeMetadata(buildRagMessageMetadata(result, scene, kbId)));
            return result.answer();
        }
        String result = aiChatFacade.chat(new ChatRequest(
                        chatScene,
                        message,
                        context,
                        conversationId,
                        userId)).content();
        saveMessage(conversationId, userId, "assistant", result, null);
        return result;
    }

    @Override
    public ChatResult query(String message, String scene, String kbId, String userId, String conversationId) {
        saveMessage(conversationId, userId, "user", message, serializeMetadata(buildUserMessageMetadata(scene, kbId)));
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);
        AiChatScene chatScene = AiChatScene.fromCode(scene);
        if (isRagRequest(chatScene, kbId)) {
            RagQueryResult result = ragFacade.query(new RagQueryRequest(message, kbId, userId, context, 5));
            saveMessage(conversationId, userId, "assistant", result.answer(), serializeMetadata(buildRagMessageMetadata(result, scene, kbId)));
            return new ChatResult(
                    result.answer(),
                    "rag",
                    scene,
                    kbId,
                    result.traceId(),
                    result.rewrittenQuery(),
                    result.citations(),
                    result.retrievedChunks());
        }
        String answer = aiChatFacade.chat(new ChatRequest(
                        chatScene,
                        message,
                        context,
                        conversationId,
                        userId)).content();
        saveMessage(conversationId, userId, "assistant", answer, null);
        return new ChatResult(
                answer,
                "chat",
                scene,
                kbId,
                null,
                null,
                List.of(),
                List.of());
    }

    @Override
    public Flux<String> stream(String message, String scene, String kbId, String userId, String conversationId) {
        saveMessage(conversationId, userId, "user", message, serializeMetadata(buildUserMessageMetadata(scene, kbId)));
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);
        AiChatScene chatScene = AiChatScene.fromCode(scene);

        if (isRagRequest(chatScene, kbId)) {
            RagQueryResult result = ragFacade.query(new RagQueryRequest(message, kbId, userId, context, 5));
            String metadataJson = serializeMetadata(buildRagMessageMetadata(result, scene, kbId));
            return Flux.just(result.answer())
                    .doOnComplete(() -> saveMessage(conversationId, userId, "assistant", result.answer(), metadataJson));
        }

        StringBuilder fullContent = new StringBuilder();
        return aiChatFacade.stream(new ChatRequest(
                        chatScene,
                        message,
                        context,
                        conversationId,
                        userId))
                .doOnNext(fullContent::append)
                .doOnComplete(() -> saveMessage(conversationId, userId, "assistant", fullContent.toString(), null));
    }

    private boolean isRagRequest(AiChatScene scene, String kbId) {
        return scene == AiChatScene.RAG_QA && kbId != null && !kbId.isBlank();
    }

    private void saveMessage(String conversationId, String userId, String role, String content, String metadataJson) {
        MessageEntity message = new MessageEntity();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setMetadataJson(metadataJson);
        messageMapper.insert(message);

        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationEntity>()
                .eq(ConversationEntity::getConversationId, conversationId)
                .set(ConversationEntity::getLastTime, LocalDateTime.now()));

        if ("user".equals(role)) {
            String cleanContent = content.trim().replaceAll("\\s+", " ");
            String autoTitle = cleanContent.length() > 30 ? cleanContent.substring(0, 30) + "..." : cleanContent;

            conversationMapper.update(null, new LambdaUpdateWrapper<ConversationEntity>()
                    .eq(ConversationEntity::getConversationId, conversationId)
                    .eq(ConversationEntity::getTitle, "New Chat")
                    .set(ConversationEntity::getTitle, autoTitle));
        }

        messageCache.invalidate(conversationId);
        sessionCache.invalidate(userId);
    }

    private Map<String, Object> buildRagMessageMetadata(RagQueryResult result, String scene, String kbId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", "rag");
        metadata.put("scene", scene);
        metadata.put("kbId", kbId);
        metadata.put("traceId", result.traceId());
        metadata.put("rewrittenQuery", result.rewrittenQuery());
        metadata.put("citations", result.citations());
        metadata.put("retrievedChunks", result.retrievedChunks());
        return metadata;
    }

    private Map<String, Object> buildUserMessageMetadata(String scene, String kbId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", AiChatScene.RAG_QA.code().equals(scene) && kbId != null && !kbId.isBlank() ? "rag" : "chat");
        if (scene != null && !scene.isBlank()) {
            metadata.put("scene", scene);
        }
        if (kbId != null && !kbId.isBlank()) {
            metadata.put("kbId", kbId);
        }
        return metadata;
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }
}
