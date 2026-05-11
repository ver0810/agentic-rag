package com.agenticrag.chat.controller;

import com.agenticrag.chat.service.ChatService;
import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.user.dao.entity.ConversationEntity;
import com.agenticrag.user.dao.entity.MessageEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ConversationEntity>> listSessions(@CurrentUser String userId) {
        return ResponseEntity.ok(chatService.listSessions(userId));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<MessageEntity>> listMessages(@RequestParam String conversationId,
                                                         @CurrentUser String userId) {
        return ResponseEntity.ok(chatService.listMessages(conversationId, userId));
    }

    @PutMapping("/session/{conversationId}/title")
    public ResponseEntity<Void> renameSession(@PathVariable String conversationId,
                                              @RequestParam String title,
                                              @CurrentUser String userId) {
        chatService.renameSession(conversationId, title, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/session/new")
    public ResponseEntity<Map<String, String>> newSession(@CurrentUser String userId) {
        String sessionId = chatService.createSession(userId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "message", "会话创建成功"
        ));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId,
                                              @CurrentUser String userId) {
        chatService.deleteSession(sessionId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public String chat(@RequestParam(name = "message") String message,
                       @RequestParam(name = "scene", required = false) String scene,
                       @RequestParam(name = "kbId", required = false) String kbId,
                       @CurrentUser String userId,
                       @RequestParam(name = "conversationId") String conversationId) {
        return chatService.chat(message, scene, kbId, userId, conversationId);
    }

    @PostMapping("/stream")
    public Flux<String> stream(@RequestParam(name = "message") String message,
                               @RequestParam(name = "scene", required = false) String scene,
                               @RequestParam(name = "kbId", required = false) String kbId,
                               @CurrentUser String userId,
                               @RequestParam(name = "conversationId") String conversationId) {
        return chatService.stream(message, scene, kbId, userId, conversationId);
    }
}
