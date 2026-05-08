package com.agenticrag.user.service;

import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.user.ai.dto.AiConfiguredModelOptionDTO;
import com.agenticrag.user.ai.dto.AiProviderOptionDTO;
import com.agenticrag.user.ai.dto.AiModelSwitchRequest;
import com.agenticrag.user.ai.dto.AiSettingsDTO;
import com.agenticrag.user.ai.dto.AiSettingsSaveRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyResponse;
import java.util.List;

public interface UserAiProviderConfigService {

    List<AiProviderOptionDTO> listProviderOptions(String userId);

    List<AiConfiguredModelOptionDTO> listConfiguredModels(String userId);

    AiSettingsDTO getCurrentSettings(String userId);

    AiSettingsVerifyResponse verifySettings(String userId, AiSettingsVerifyRequest request);

    void saveSettings(String userId, AiSettingsSaveRequest request);

    void clearSettings(String userId);

    void switchModel(String userId, AiModelSwitchRequest request);

    AiRuntimeOptions resolveRuntimeOptions(String userId);
}
