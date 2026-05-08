package com.agenticrag.user.controller;

import com.agenticrag.user.ai.dto.AiSettingsDTO;
import com.agenticrag.user.ai.dto.AiSettingsSaveRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyResponse;
import com.agenticrag.user.service.UserAiProviderConfigService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/ai-settings")
public class UserAiSettingsController {

    private final UserAiProviderConfigService userAiProviderConfigService;

    public UserAiSettingsController(UserAiProviderConfigService userAiProviderConfigService) {
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    @GetMapping
    public AiSettingsDTO getSettings(@RequestHeader("X-User-Id") String userId) {
        return userAiProviderConfigService.getCurrentSettings(userId);
    }

    @PostMapping("/verify")
    public AiSettingsVerifyResponse verify(@RequestHeader("X-User-Id") String userId,
                                           @RequestBody AiSettingsVerifyRequest request) {
        return userAiProviderConfigService.verifySettings(userId, request);
    }

    @PostMapping("/save")
    public void save(@RequestHeader("X-User-Id") String userId,
                     @RequestBody AiSettingsSaveRequest request) {
        userAiProviderConfigService.saveSettings(userId, request);
    }

    @DeleteMapping
    public void clear(@RequestHeader("X-User-Id") String userId) {
        userAiProviderConfigService.clearSettings(userId);
    }
}
