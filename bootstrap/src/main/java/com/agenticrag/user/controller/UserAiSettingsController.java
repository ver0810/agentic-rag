package com.agenticrag.user.controller;

import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.user.ai.dto.AiConfiguredModelOptionDTO;
import com.agenticrag.user.ai.dto.AiModelSwitchRequest;
import com.agenticrag.user.ai.dto.AiSettingsDTO;
import com.agenticrag.user.ai.dto.AiSettingsSaveRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyResponse;
import com.agenticrag.user.service.UserAiProviderConfigService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/user/ai-settings")
public class UserAiSettingsController {

    private final UserAiProviderConfigService userAiProviderConfigService;

    public UserAiSettingsController(UserAiProviderConfigService userAiProviderConfigService) {
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    @GetMapping
    public AiSettingsDTO getSettings(@CurrentUser String userId) {
        return userAiProviderConfigService.getCurrentSettings(userId);
    }

    @GetMapping("/options")
    public List<AiConfiguredModelOptionDTO> listConfiguredModels(@CurrentUser String userId) {
        return userAiProviderConfigService.listConfiguredModels(userId);
    }

    @PostMapping("/verify")
    public AiSettingsVerifyResponse verify(@CurrentUser String userId,
                                           @RequestBody AiSettingsVerifyRequest request) {
        return userAiProviderConfigService.verifySettings(userId, request);
    }

    @PostMapping("/save")
    public void save(@CurrentUser String userId,
                     @RequestBody AiSettingsSaveRequest request) {
        userAiProviderConfigService.saveSettings(userId, request);
    }

    @PostMapping("/switch")
    public void switchModel(@CurrentUser String userId,
                            @RequestBody AiModelSwitchRequest request) {
        userAiProviderConfigService.switchModel(userId, request);
    }

    @DeleteMapping
    public void clear(@CurrentUser String userId) {
        userAiProviderConfigService.clearSettings(userId);
    }
}
