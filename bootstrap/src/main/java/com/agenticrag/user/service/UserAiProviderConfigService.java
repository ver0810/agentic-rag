package com.agenticrag.user.service;

import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.user.ai.dto.AiProviderOptionDTO;
import com.agenticrag.user.ai.dto.AiSettingsDTO;
import com.agenticrag.user.ai.dto.AiSettingsSaveRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyResponse;
import java.util.List;

public interface UserAiProviderConfigService {

    List<AiProviderOptionDTO> listProviderOptions(String userId);

    AiSettingsDTO getCurrentSettings(String userId);

    AiSettingsVerifyResponse verifySettings(String userId, AiSettingsVerifyRequest request);

    void saveSettings(String userId, AiSettingsSaveRequest request);

    void clearSettings(String userId);

    AiRuntimeOptions resolveRuntimeOptions(String userId);
}
