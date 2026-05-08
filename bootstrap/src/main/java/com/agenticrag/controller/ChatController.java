package com.agenticrag.controller;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.user.service.UserAiProviderConfigService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/chat")
public class ChatController {

    private final AiChatService aiChatService;
    private final UserAiProviderConfigService userAiProviderConfigService;

    public ChatController(AiChatService aiChatService,
                          UserAiProviderConfigService userAiProviderConfigService) {
        this.aiChatService = aiChatService;
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    @PostMapping
    public String chat(@RequestParam(name = "message") String message,
                       @RequestParam(name = "scene", required = false) String scene,
                       @CurrentUser String userId) {
        AiRuntimeOptions runtimeOptions = userAiProviderConfigService.resolveRuntimeOptions(userId);
        return aiChatService.call(AiChatScene.fromCode(scene), message, runtimeOptions);
    }


    @PostMapping("/stream")
    public Flux<String> stream(@RequestParam(name = "message") String message,
                               @RequestParam(name = "scene", required = false) String scene,
                               @CurrentUser String userId) {
        AiRuntimeOptions runtimeOptions = userAiProviderConfigService.resolveRuntimeOptions(userId);
        return aiChatService.stream(AiChatScene.fromCode(scene), message, runtimeOptions);
    }
}
