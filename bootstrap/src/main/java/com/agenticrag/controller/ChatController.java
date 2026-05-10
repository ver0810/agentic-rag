package com.agenticrag.controller;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.user.dao.entity.ConversationDao;
import com.agenticrag.user.dao.entity.MessageDao;
import com.agenticrag.user.dao.mapper.ConversationMapper;
import com.agenticrag.user.dao.mapper.MessageMapper;
import com.agenticrag.user.service.UserAiProviderConfigService;
import com.agenticrag.utils.SessionIdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/chat")
public class ChatController {

    private final AiChatService aiChatService;
    private final UserAiProviderConfigService userAiProviderConfigService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final Cache<String, List<MessageDao>> messageCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    private final Cache<String, List<ConversationDao>> sessionCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();

    public ChatController(AiChatService aiChatService,
                          UserAiProviderConfigService userAiProviderConfigService,
                          ConversationMapper conversationMapper,
                          MessageMapper messageMapper) {
        this.aiChatService = aiChatService;
        this.userAiProviderConfigService = userAiProviderConfigService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ConversationDao>> listSessions(@CurrentUser String userId) {
        List<ConversationDao> sessions = sessionCache.get(userId, id ->
                conversationMapper.selectList(
                        new LambdaQueryWrapper<ConversationDao>()
                                .eq(ConversationDao::getUserId, id)
                                .eq(ConversationDao::getDeleted, 0)
                                .orderByDesc(ConversationDao::getLastTime)
                )
        );
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/messages")
    public ResponseEntity<List<MessageDao>> listMessages(@RequestParam String conversationId, @CurrentUser String userId) {
        List<MessageDao> messages = messageCache.get(conversationId, id ->
                messageMapper.selectList(
                        new LambdaQueryWrapper<MessageDao>()
                                .eq(MessageDao::getConversationId, id)
                                .eq(MessageDao::getUserId, userId)
                                .eq(MessageDao::getDeleted, 0)
                                .orderByAsc(MessageDao::getCreateTime)
                )
        );
        return ResponseEntity.ok(messages);
    }

    @PutMapping("/session/{conversationId}/title")
    public ResponseEntity<Void> renameSession(@PathVariable String conversationId,
                                              @RequestParam String title,
                                              @CurrentUser String userId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationDao>()
                .eq(ConversationDao::getConversationId, conversationId)
                .eq(ConversationDao::getUserId, userId)
                .set(ConversationDao::getTitle, title));
        sessionCache.invalidate(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/new")
    public ResponseEntity<Map<String, String>> newSession(@CurrentUser String userId) {
        String sessionId = SessionIdGenerator.generate();

        ConversationDao conversation = new ConversationDao();
        conversation.setConversationId(sessionId);
        conversation.setUserId(userId);
        conversation.setTitle("New Chat");
        conversation.setLastTime(java.time.LocalDateTime.now());
        conversationMapper.insert(conversation);

        sessionCache.invalidate(userId);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "message", "会话创建成功"
        ));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId, @CurrentUser String userId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<ConversationDao>()
                .eq(ConversationDao::getConversationId, sessionId)
                .eq(ConversationDao::getUserId, userId)
                .set(ConversationDao::getDeleted, 1));
        sessionCache.invalidate(userId);
        messageCache.invalidate(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public String chat(@RequestParam(name = "message") String message,
                       @RequestParam(name = "scene", required = false) String scene,
                       @RequestParam(name = "kbId", required = false) String kbId,
                       @CurrentUser String userId,
                       @RequestParam(name = "conversationId") String conversationId) {
        saveMessage(conversationId, userId, "user", message);
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);
        String result = aiChatService.call(AiChatScene.fromCode(scene), message, context, conversationId, kbId);
        saveMessage(conversationId, userId, "assistant", result);
        return result;
    }


    @PostMapping("/stream")
    public Flux<String> stream(@RequestParam(name = "message") String message,
                               @RequestParam(name = "scene", required = false) String scene,
                               @RequestParam(name = "kbId", required = false) String kbId,
                               @CurrentUser String userId,
                               @RequestParam(name = "conversationId") String conversationId) {
        saveMessage(conversationId, userId, "user", message);
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);

        StringBuilder fullContent = new StringBuilder();
        return aiChatService.stream(AiChatScene.fromCode(scene), message, context, conversationId, kbId)
                .doOnNext(fullContent::append)
                .doOnComplete(() -> saveMessage(conversationId, userId, "assistant", fullContent.toString()));
    }

    private void saveMessage(String conversationId, String userId, String role, String content) {
        MessageDao message = new MessageDao();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        messageMapper.insert(message);

        // Update conversation last time and optionally title
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConversationDao> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConversationDao>()
                        .eq(ConversationDao::getConversationId, conversationId)
                        .set(ConversationDao::getLastTime, java.time.LocalDateTime.now());

        // If it's the first user message, automatically set title
        if ("user".equals(role)) {
            String cleanContent = content.trim().replaceAll("\\s+", " ");
            String autoTitle = cleanContent.length() > 30 ? cleanContent.substring(0, 30) + "..." : cleanContent;

            // Only update if current title is "New Chat"
            conversationMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConversationDao>()
                    .eq(ConversationDao::getConversationId, conversationId)
                    .eq(ConversationDao::getTitle, "New Chat")
                    .set(ConversationDao::getTitle, autoTitle));
        }

        conversationMapper.update(null, updateWrapper);
        messageCache.invalidate(conversationId);
        sessionCache.invalidate(userId);
    }
}
