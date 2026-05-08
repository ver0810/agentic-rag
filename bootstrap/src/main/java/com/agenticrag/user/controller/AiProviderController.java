package com.agenticrag.user.controller;

import com.agenticrag.user.ai.dto.AiProviderOptionDTO;
import com.agenticrag.user.service.UserAiProviderConfigService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/providers")
public class AiProviderController {

    private final UserAiProviderConfigService userAiProviderConfigService;

    public AiProviderController(UserAiProviderConfigService userAiProviderConfigService) {
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    @GetMapping
    public List<AiProviderOptionDTO> listProviders(@RequestHeader("X-User-Id") String userId) {
        return userAiProviderConfigService.listProviderOptions(userId);
    }
}
