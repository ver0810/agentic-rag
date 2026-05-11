package com.agenticrag.user.service.impl;

import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.model.OpenAiRuntimeOptions;
import com.agenticrag.user.ai.dto.AiConfiguredModelOptionDTO;
import com.agenticrag.infra.ai.service.OpenAiCompatibleModelFactory;
import com.agenticrag.user.ai.dto.AiProviderCatalog;
import com.agenticrag.user.ai.dto.AiModelSwitchRequest;
import com.agenticrag.user.ai.dto.AiProviderModelDTO;
import com.agenticrag.user.ai.dto.AiProviderOptionDTO;
import com.agenticrag.user.ai.dto.AiSettingsDTO;
import com.agenticrag.user.ai.dto.AiSettingsSaveRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyRequest;
import com.agenticrag.user.ai.dto.AiSettingsVerifyResponse;
import com.agenticrag.user.dao.entity.UserAiProviderConfigEntity;
import com.agenticrag.user.dao.mapper.UserAiProviderConfigMapper;
import com.agenticrag.user.service.UserAiProviderConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class UserAiProviderConfigServiceImpl
        extends ServiceImpl<UserAiProviderConfigMapper, UserAiProviderConfigEntity>
        implements UserAiProviderConfigService {

    private static final Map<String, AiProviderCatalog> PROVIDERS = createProviders();

    private final OpenAiCompatibleModelFactory modelFactory;

    public UserAiProviderConfigServiceImpl(OpenAiCompatibleModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public List<AiProviderOptionDTO> listProviderOptions(String userId) {
        Assert.hasText(userId, "用户ID不能为空");
        Map<String, UserAiProviderConfigEntity> userConfigs = list(new LambdaQueryWrapper<UserAiProviderConfigEntity>()
                .eq(UserAiProviderConfigEntity::getUserId, userId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(UserAiProviderConfigEntity::getProvider, item -> item, (a, b) -> a));
        return PROVIDERS.values().stream().map(provider -> {
            AiProviderOptionDTO dto = new AiProviderOptionDTO(provider.getProvider(), provider.getDisplayName());
            UserAiProviderConfigEntity config = userConfigs.get(provider.getProvider());
            dto.setConfigured(config != null && StringUtils.hasText(config.getApiKey()));
            dto.setVerified(config != null && config.getVerified() != null && config.getVerified() == 1);
            dto.setEnabled(config != null && config.getEnabled() != null && config.getEnabled() == 1);
            return dto;
        }).toList();
    }

    @Override
    public List<AiConfiguredModelOptionDTO> listConfiguredModels(String userId) {
        Assert.hasText(userId, "用户ID不能为空");
        return list(new LambdaQueryWrapper<UserAiProviderConfigEntity>()
                .eq(UserAiProviderConfigEntity::getUserId, userId)
                .eq(UserAiProviderConfigEntity::getVerified, 1)
                .orderByDesc(UserAiProviderConfigEntity::getEnabled)
                .orderByDesc(UserAiProviderConfigEntity::getUpdateTime))
                .stream()
                .flatMap(config -> {
                    AiProviderCatalog catalog = requireProvider(config.getProvider());
                    DiscoveredModels discoveredModels = resolveAllowedModels(config, catalog);
                    List<AiProviderModelDTO> chatModels = discoveredModels.chatModels.isEmpty() ? catalog.getChatModels() : discoveredModels.chatModels;
                    String activeChatModel = StringUtils.hasText(config.getChatModel()) ? config.getChatModel() : catalog.getDefaultChatModel();
                    String activeEmbeddingModel = StringUtils.hasText(config.getEmbeddingModel()) ? config.getEmbeddingModel() : catalog.getDefaultEmbeddingModel();
                    return chatModels.stream().map(model -> {
                        AiConfiguredModelOptionDTO dto = new AiConfiguredModelOptionDTO();
                        dto.setProvider(config.getProvider());
                        dto.setProviderName(catalog.getDisplayName());
                        dto.setChatModel(model.getModelCode());
                        dto.setEmbeddingModel(activeEmbeddingModel);
                        dto.setActive(config.getEnabled() != null && config.getEnabled() == 1 && model.getModelCode().equals(activeChatModel));
                        dto.setVerified(config.getVerified() != null && config.getVerified() == 1);
                        dto.setRecommended(Boolean.TRUE.equals(model.getRecommended()));
                        return dto;
                    });
                })
                .toList();
    }

    @Override
    public AiSettingsDTO getCurrentSettings(String userId) {
        Assert.hasText(userId, "用户ID不能为空");
        UserAiProviderConfigEntity activeConfig = getActiveDao(userId);
        if (activeConfig != null) {
            return toSettingsDto(activeConfig, requireProvider(activeConfig.getProvider()));
        }
        List<UserAiProviderConfigEntity> configs = list(new LambdaQueryWrapper<UserAiProviderConfigEntity>()
                .eq(UserAiProviderConfigEntity::getUserId, userId)
                .orderByDesc(UserAiProviderConfigEntity::getUpdateTime)
                .last("limit 1"));
        if (!configs.isEmpty()) {
            UserAiProviderConfigEntity config = configs.get(0);
            return toSettingsDto(config, requireProvider(config.getProvider()));
        }
        AiProviderCatalog defaultCatalog = PROVIDERS.values().iterator().next();
        return toSettingsDto(null, defaultCatalog);
    }

    @Override
    public AiSettingsVerifyResponse verifySettings(String userId, AiSettingsVerifyRequest request) {
        Assert.hasText(userId, "用户ID不能为空");
        Assert.notNull(request, "请求不能为空");
        Assert.hasText(request.getProvider(), "provider不能为空");
        Assert.hasText(request.getApiKey(), "apiKey不能为空");
        saveApiKey(userId, request.getProvider(), request.getApiKey());
        VerifyResult verifyResponse = verifyConfig(userId, request.getProvider());
        AiProviderCatalog catalog = requireProvider(request.getProvider());
        AiSettingsVerifyResponse response = new AiSettingsVerifyResponse();
        response.setSuccess(verifyResponse.getSuccess());
        response.setMessage(verifyResponse.getMessage());
        response.setProvider(request.getProvider());
        response.setVerifiedAt(verifyResponse.getVerifiedAt());
        response.setChatModels(verifyResponse.getChatModels());
        response.setEmbeddingModels(verifyResponse.getEmbeddingModels());
        return response;
    }

    @Override
    public void saveSettings(String userId, AiSettingsSaveRequest request) {
        Assert.hasText(userId, "用户ID不能为空");
        Assert.notNull(request, "请求不能为空");
        Assert.hasText(request.getProvider(), "provider不能为空");

        if (StringUtils.hasText(request.getApiKey())) {
            UserAiProviderConfigEntity existingConfig = findByUserIdAndProvider(userId, request.getProvider());
            String normalizedApiKey = request.getApiKey().trim();
            if (existingConfig == null || !normalizedApiKey.equals(existingConfig.getApiKey())) {
                saveApiKey(userId, request.getProvider(), normalizedApiKey);
            }
        }

        UserAiProviderConfigEntity config = findByUserIdAndProvider(userId, request.getProvider());
        Assert.notNull(config, "请先配置API Key");
        Assert.isTrue(config.getVerified() != null && config.getVerified() == 1, "请先验证API Key");

        selectModels(userId, request.getProvider(), request.getChatModel(), request.getEmbeddingModel(), true);
    }

    @Override
    public void clearSettings(String userId) {
        Assert.hasText(userId, "用户ID不能为空");
        List<UserAiProviderConfigEntity> configs = list(new LambdaQueryWrapper<UserAiProviderConfigEntity>()
                .eq(UserAiProviderConfigEntity::getUserId, userId));
        if (configs.isEmpty()) {
            return;
        }
        for (UserAiProviderConfigEntity config : configs) {
            config.setEnabled(0);
            updateById(config);
        }
    }

    @Override
    public void switchModel(String userId, AiModelSwitchRequest request) {
        Assert.hasText(userId, "用户ID不能为空");
        Assert.notNull(request, "请求不能为空");
        Assert.hasText(request.getProvider(), "provider不能为空");

        UserAiProviderConfigEntity config = findByUserIdAndProvider(userId, request.getProvider());
        Assert.notNull(config, "该Provider尚未配置");
        Assert.isTrue(config.getVerified() != null && config.getVerified() == 1, "该Provider尚未验证");

        AiProviderCatalog catalog = requireProvider(request.getProvider());
        validateSelectedModels(config, catalog, request.getChatModel(), request.getEmbeddingModel());

        disableOtherConfigs(userId, config.getId());
        if (StringUtils.hasText(request.getChatModel())) {
            config.setChatModel(request.getChatModel());
        }
        if (StringUtils.hasText(request.getEmbeddingModel())) {
            config.setEmbeddingModel(request.getEmbeddingModel());
        }
        config.setEnabled(1);
        updateById(config);
    }

    private String saveApiKey(String userId, String provider, String apiKey) {
        Assert.hasText(userId, "用户ID不能为空");
        Assert.hasText(provider, "provider不能为空");
        Assert.hasText(apiKey, "apiKey不能为空");
        validateApiKey(apiKey);

        AiProviderCatalog catalog = requireProvider(provider);
        UserAiProviderConfigEntity config = findByUserIdAndProvider(userId, provider);
        if (config == null) {
            config = new UserAiProviderConfigEntity();
            config.setUserId(userId);
            config.setProvider(provider);
            config.setChatModel(catalog.getDefaultChatModel());
            config.setEmbeddingModel(catalog.getDefaultEmbeddingModel());
            config.setEnabled(0);
        }
        config.setBaseUrl(catalog.getBaseUrl());
        config.setApiKey(apiKey.trim());
        config.setVerified(0);
        config.setLastVerifiedAt(null);
        saveOrUpdate(config);
        return config.getId();
    }

    private VerifyResult verifyConfig(String userId, String provider) {
        Assert.hasText(userId, "用户ID不能为空");
        AiProviderCatalog catalog = requireProvider(provider);
        UserAiProviderConfigEntity config = findByUserIdAndProvider(userId, provider);
        Assert.notNull(config, "请先保存API Key");
        Assert.hasText(config.getApiKey(), "请先保存API Key");

        VerifyResult response = new VerifyResult();
        try {
            OpenAiRuntimeOptions runtimeOptions = buildRuntimeOptions(config, catalog);
            List<OpenAiCompatibleModelFactory.AvailableModel> availableModels = modelFactory.listModels(runtimeOptions);
            DiscoveredModels discoveredModels = classifyModels(catalog, availableModels);
            LocalDateTime verifiedAt = LocalDateTime.now();
            config.setVerified(1);
            config.setLastVerifiedAt(verifiedAt);
            updateById(config);
            response.setSuccess(true);
            response.setMessage("连接成功");
            response.setVerifiedAt(verifiedAt);
            response.setChatModels(discoveredModels.chatModels);
            response.setEmbeddingModels(discoveredModels.embeddingModels);
            return response;
        } catch (RestClientResponseException ex) {
            config.setVerified(0);
            updateById(config);
            response.setSuccess(false);
            response.setMessage("连接失败: " + ex.getRawStatusCode() + " " + ex.getStatusText());
            response.setChatModels(catalog.getChatModels());
            response.setEmbeddingModels(catalog.getEmbeddingModels());
            return response;
        } catch (WebClientResponseException ex) {
            config.setVerified(0);
            updateById(config);
            response.setSuccess(false);
            response.setMessage("连接失败: " + ex.getStatusCode().value() + " " + ex.getStatusText());
            response.setChatModels(catalog.getChatModels());
            response.setEmbeddingModels(catalog.getEmbeddingModels());
            return response;
        } catch (Exception ex) {
            config.setVerified(0);
            updateById(config);
            response.setSuccess(false);
            response.setMessage("连接失败: " + ex.getMessage());
            response.setChatModels(catalog.getChatModels());
            response.setEmbeddingModels(catalog.getEmbeddingModels());
            return response;
        }
    }

    private String selectModels(String userId,
                                String provider,
                                String chatModel,
                                String embeddingModel,
                                boolean enabled) {
        Assert.hasText(userId, "用户ID不能为空");
        Assert.hasText(provider, "provider不能为空");

        AiProviderCatalog catalog = requireProvider(provider);
        UserAiProviderConfigEntity config = findByUserIdAndProvider(userId, provider);
        Assert.notNull(config, "请先保存API Key");
        Assert.isTrue(config.getVerified() != null && config.getVerified() == 1, "请先验证API Key");
        validateSelectedModels(config, catalog, chatModel, embeddingModel);

        config.setBaseUrl(catalog.getBaseUrl());
        config.setChatModel(StringUtils.hasText(chatModel) ? chatModel : catalog.getDefaultChatModel());
        config.setEmbeddingModel(StringUtils.hasText(embeddingModel) ? embeddingModel : catalog.getDefaultEmbeddingModel());
        config.setEnabled(enabled ? 1 : 0);

        if (config.getEnabled() == 1) {
            disableOtherConfigs(userId, config.getId());
        }
        updateById(config);
        if (config.getEnabled() == 1) {
            disableOtherConfigs(userId, config.getId());
        }
        return config.getId();
    }

    @Override
    public AiRuntimeContext resolveRuntimeContext(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        UserAiProviderConfigEntity config = getActiveDao(userId);
        if (config == null) {
            return null;
        }
        return new AiRuntimeContext(buildRuntimeOptions(config, requireProvider(config.getProvider())));
    }

    private UserAiProviderConfigEntity getActiveDao(String userId) {
        Assert.hasText(userId, "用户ID不能为空");
        return getOne(new LambdaQueryWrapper<UserAiProviderConfigEntity>()
                .eq(UserAiProviderConfigEntity::getUserId, userId)
                .eq(UserAiProviderConfigEntity::getEnabled, 1)
                .last("limit 1"));
    }

    private void disableOtherConfigs(String userId, String currentId) {
        LambdaUpdateWrapper<UserAiProviderConfigEntity> wrapper = new LambdaUpdateWrapper<UserAiProviderConfigEntity>()
                .eq(UserAiProviderConfigEntity::getUserId, userId)
                .set(UserAiProviderConfigEntity::getEnabled, 0);
        if (StringUtils.hasText(currentId)) {
            wrapper.ne(UserAiProviderConfigEntity::getId, currentId);
        }
        update(wrapper);
    }

    private AiSettingsDTO toSettingsDto(UserAiProviderConfigEntity dao, AiProviderCatalog catalog) {
        AiSettingsDTO dto = new AiSettingsDTO();
        dto.setProvider(catalog.getProvider());
        dto.setProviderName(catalog.getDisplayName());
        dto.setAvailableChatModels(catalog.getChatModels());
        dto.setAvailableEmbeddingModels(catalog.getEmbeddingModels());
        if (dao != null) {
            dto.setHasApiKey(StringUtils.hasText(dao.getApiKey()));
            dto.setApiKeyMasked(maskApiKey(dao.getApiKey()));
            dto.setVerified(dao.getVerified() != null && dao.getVerified() == 1);
            dto.setLastVerifiedAt(dao.getLastVerifiedAt());
            dto.setChatModel(StringUtils.hasText(dao.getChatModel()) ? dao.getChatModel() : catalog.getDefaultChatModel());
            dto.setEmbeddingModel(StringUtils.hasText(dao.getEmbeddingModel()) ? dao.getEmbeddingModel() : catalog.getDefaultEmbeddingModel());
        } else {
            dto.setHasApiKey(false);
            dto.setApiKeyMasked("");
            dto.setVerified(false);
            dto.setChatModel(catalog.getDefaultChatModel());
            dto.setEmbeddingModel(catalog.getDefaultEmbeddingModel());
        }
        return dto;
    }

    private static class VerifyResult {
        private boolean success;
        private String message;
        private LocalDateTime verifiedAt;
        private List<AiProviderModelDTO> chatModels = List.of();
        private List<AiProviderModelDTO> embeddingModels = List.of();

        public boolean getSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public LocalDateTime getVerifiedAt() {
            return verifiedAt;
        }

        public void setVerifiedAt(LocalDateTime verifiedAt) {
            this.verifiedAt = verifiedAt;
        }

        public List<AiProviderModelDTO> getChatModels() {
            return chatModels;
        }

        public void setChatModels(List<AiProviderModelDTO> chatModels) {
            this.chatModels = chatModels;
        }

        public List<AiProviderModelDTO> getEmbeddingModels() {
            return embeddingModels;
        }

        public void setEmbeddingModels(List<AiProviderModelDTO> embeddingModels) {
            this.embeddingModels = embeddingModels;
        }
    }

    private static class DiscoveredModels {
        private List<AiProviderModelDTO> chatModels = List.of();
        private List<AiProviderModelDTO> embeddingModels = List.of();
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "********";
        }
        return apiKey.substring(0, 4) + "********" + apiKey.substring(apiKey.length() - 4);
    }

    private UserAiProviderConfigEntity findByUserIdAndProvider(String userId, String provider) {
        return getOne(new LambdaQueryWrapper<UserAiProviderConfigEntity>()
                .eq(UserAiProviderConfigEntity::getUserId, userId)
                .eq(UserAiProviderConfigEntity::getProvider, provider)
                .last("limit 1"));
    }

    private AiProviderCatalog requireProvider(String provider) {
        AiProviderCatalog catalog = PROVIDERS.get(provider);
        Assert.notNull(catalog, "不支持的provider: " + provider);
        return catalog;
    }

    private OpenAiRuntimeOptions buildRuntimeOptions(UserAiProviderConfigEntity config, AiProviderCatalog catalog) {
        OpenAiRuntimeOptions options = new OpenAiRuntimeOptions();
        options.setProvider(catalog.getProvider());
        options.setBaseUrl(catalog.getBaseUrl());
        options.setCompletionsPath(catalog.getCompletionsPath());
        options.setEmbeddingsPath(catalog.getEmbeddingsPath());
        options.setModelsPath(catalog.getModelsPath());
        options.setApiKey(config.getApiKey());
        options.setChatModel(StringUtils.hasText(config.getChatModel()) ? config.getChatModel() : catalog.getDefaultChatModel());
        options.setEmbeddingModel(StringUtils.hasText(config.getEmbeddingModel()) ? config.getEmbeddingModel() : catalog.getDefaultEmbeddingModel());
        return options;
    }

    private void validateSelectedModels(UserAiProviderConfigEntity config,
                                        AiProviderCatalog catalog,
                                        String chatModel,
                                        String embeddingModel) {
        DiscoveredModels discoveredModels = resolveAllowedModels(config, catalog);
        if (StringUtils.hasText(chatModel)) {
            boolean exists = discoveredModels.chatModels.stream().anyMatch(item -> item.getModelCode().equals(chatModel));
            Assert.isTrue(exists, "chatModel不在该提供商支持列表内");
        }
        if (StringUtils.hasText(embeddingModel)) {
            boolean exists = discoveredModels.embeddingModels.stream().anyMatch(item -> item.getModelCode().equals(embeddingModel));
            Assert.isTrue(exists, "embeddingModel不在该提供商支持列表内");
        }
    }

    private DiscoveredModels resolveAllowedModels(UserAiProviderConfigEntity config, AiProviderCatalog catalog) {
        try {
            OpenAiRuntimeOptions runtimeOptions = buildRuntimeOptions(config, catalog);
            List<OpenAiCompatibleModelFactory.AvailableModel> availableModels = modelFactory.listModels(runtimeOptions);
            return classifyModels(catalog, availableModels);
        } catch (Exception ignored) {
            DiscoveredModels fallback = new DiscoveredModels();
            fallback.chatModels = catalog.getChatModels();
            fallback.embeddingModels = catalog.getEmbeddingModels();
            return fallback;
        }
    }

    private void validateApiKey(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        Assert.hasText(normalized, "apiKey不能为空");
        Assert.isTrue(!normalized.contains(" "), "apiKey格式不正确，请重新粘贴原始 API Key");
        Assert.isTrue(!normalized.contains("\n") && !normalized.contains("\r"), "apiKey格式不正确，请重新粘贴原始 API Key");
        Assert.isTrue(!normalized.startsWith("Verification Failed"), "apiKey格式不正确，请不要粘贴错误提示文本");
        Assert.isTrue(!normalized.startsWith("连接失败"), "apiKey格式不正确，请不要粘贴错误提示文本");
    }

    private DiscoveredModels classifyModels(AiProviderCatalog catalog,
                                            List<OpenAiCompatibleModelFactory.AvailableModel> availableModels) {
        if (availableModels == null || availableModels.isEmpty()) {
            DiscoveredModels fallback = new DiscoveredModels();
            fallback.chatModels = catalog.getChatModels();
            fallback.embeddingModels = catalog.getEmbeddingModels();
            return fallback;
        }

        List<AiProviderModelDTO> chatModels = new ArrayList<>();
        List<AiProviderModelDTO> embeddingModels = new ArrayList<>();
        for (OpenAiCompatibleModelFactory.AvailableModel model : availableModels) {
            String modelId = model.getId();
            String lower = modelId.toLowerCase();
            if (isEmbeddingModel(model, lower)) {
                embeddingModels.add(new AiProviderModelDTO(modelId, model.getDisplayName(), "embedding", false));
            } else if (isChatModel(model, lower)) {
                chatModels.add(new AiProviderModelDTO(modelId, model.getDisplayName(), "chat", false));
            }
        }

        if (chatModels.isEmpty()) {
            chatModels = catalog.getChatModels();
        } else {
            markRecommended(chatModels, catalog.getDefaultChatModel());
        }
        if (embeddingModels.isEmpty()) {
            embeddingModels = catalog.getEmbeddingModels();
        } else {
            markRecommended(embeddingModels, catalog.getDefaultEmbeddingModel());
        }

        DiscoveredModels discoveredModels = new DiscoveredModels();
        discoveredModels.chatModels = chatModels;
        discoveredModels.embeddingModels = embeddingModels;
        return discoveredModels;
    }

    private boolean isEmbeddingModel(OpenAiCompatibleModelFactory.AvailableModel model, String lower) {
        if (lower.contains("embedding") || lower.contains("bge-") || lower.contains("bge_") || lower.contains("embed")) {
            return true;
        }
        return "text->embedding".equalsIgnoreCase(model.getModality())
                || model.getOutputModalities().stream().anyMatch(item -> "embedding".equalsIgnoreCase(item));
    }

    private boolean isChatModel(OpenAiCompatibleModelFactory.AvailableModel model, String lower) {
        if (lower.contains("tts") || lower.contains("transcribe") || lower.contains("whisper") || lower.contains("speech")) {
            return false;
        }
        if (model.getOutputModalities().stream().anyMatch(item -> "text".equalsIgnoreCase(item))) {
            return true;
        }
        return !isEmbeddingModel(model, lower);
    }

    private void markRecommended(List<AiProviderModelDTO> models, String defaultModel) {
        for (AiProviderModelDTO model : models) {
            model.setRecommended(StringUtils.hasText(defaultModel) && defaultModel.equals(model.getModelCode()));
        }
        if (!StringUtils.hasText(defaultModel) && !models.isEmpty()) {
            models.get(0).setRecommended(true);
        }
    }

    private static Map<String, AiProviderCatalog> createProviders() {
        Map<String, AiProviderCatalog> providers = new LinkedHashMap<>();
        providers.put("deepseek", new AiProviderCatalog(
                "deepseek",
                "DeepSeek",
                "https://api.deepseek.com",
                null,
                null,
                null,
                "deepseek-v4-flash",
                "",
                List.of(
                        new AiProviderModelDTO("deepseek-v4-flash", "DeepSeek V4 Flash", "chat", true),
                        new AiProviderModelDTO("deepseek-v4-pro", "DeepSeek V4 Pro", "chat", false),
                        new AiProviderModelDTO("deepseek-chat", "DeepSeek Chat (Legacy Alias)", "chat", false),
                        new AiProviderModelDTO("deepseek-reasoner", "DeepSeek Reasoner (Legacy Alias)", "chat", false)
                ),
                List.of()
        ));
        providers.put("qwen", new AiProviderCatalog(
                "qwen",
                "Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode",
                null,
                null,
                null,
                "qwen-plus",
                "text-embedding-v3",
                List.of(
                        new AiProviderModelDTO("qwen-plus", "Qwen Plus", "chat", true),
                        new AiProviderModelDTO("qwen-turbo", "Qwen Turbo", "chat", false),
                        new AiProviderModelDTO("qwen-max", "Qwen Max", "chat", false)
                ),
                List.of(
                        new AiProviderModelDTO("text-embedding-v3", "Text Embedding V3", "embedding", true)
                )
        ));
        providers.put("moonshot", new AiProviderCatalog(
                "moonshot",
                "Moonshot",
                "https://api.moonshot.cn",
                null,
                null,
                null,
                "moonshot-v1-8k",
                "",
                List.of(
                        new AiProviderModelDTO("moonshot-v1-8k", "Moonshot V1 8K", "chat", true),
                        new AiProviderModelDTO("moonshot-v1-32k", "Moonshot V1 32K", "chat", false),
                        new AiProviderModelDTO("moonshot-v1-128k", "Moonshot V1 128K", "chat", false)
                ),
                List.of()
        ));
        providers.put("zhipu", new AiProviderCatalog(
                "zhipu",
                "Zhipu GLM",
                "https://open.bigmodel.cn",
                "/api/paas/v4/chat/completions",
                "/api/paas/v4/embeddings",
                "/api/paas/v4/models",
                "glm-4.7",
                "embedding-3",
                List.of(
                        new AiProviderModelDTO("glm-4.7", "GLM 4.7", "chat", true),
                        new AiProviderModelDTO("glm-4.5-air", "GLM 4.5 Air", "chat", false),
                        new AiProviderModelDTO("glm-4.5", "GLM 4.5", "chat", false)
                ),
                List.of(
                        new AiProviderModelDTO("embedding-3", "Embedding 3", "embedding", true)
                )
        ));
        providers.put("siliconflow", new AiProviderCatalog(
                "siliconflow",
                "SiliconFlow",
                "https://api.siliconflow.cn",
                null,
                null,
                null,
                "Qwen/Qwen3-32B",
                "BAAI/bge-m3",
                List.of(
                        new AiProviderModelDTO("Qwen/Qwen3-32B", "Qwen3 32B", "chat", true),
                        new AiProviderModelDTO("deepseek-ai/DeepSeek-V3", "DeepSeek V3", "chat", false),
                        new AiProviderModelDTO("THUDM/GLM-4-32B-0414", "GLM 4 32B", "chat", false)
                ),
                List.of(
                        new AiProviderModelDTO("BAAI/bge-m3", "BAAI BGE M3", "embedding", true),
                        new AiProviderModelDTO("BAAI/bge-large-zh-v1.5", "BAAI BGE Large ZH 1.5", "embedding", false)
                )
        ));
        providers.put("openai", new AiProviderCatalog(
                "openai",
                "OpenAI",
                "https://api.openai.com",
                null,
                null,
                null,
                "gpt-4.1-mini",
                "text-embedding-3-small",
                List.of(
                        new AiProviderModelDTO("gpt-4.1-mini", "GPT-4.1 Mini", "chat", true),
                        new AiProviderModelDTO("gpt-4o-mini", "GPT-4o Mini", "chat", false),
                        new AiProviderModelDTO("gpt-4.1", "GPT-4.1", "chat", false)
                ),
                List.of(
                        new AiProviderModelDTO("text-embedding-3-small", "Text Embedding 3 Small", "embedding", true),
                        new AiProviderModelDTO("text-embedding-3-large", "Text Embedding 3 Large", "embedding", false)
                )
        ));
        providers.put("openrouter", new AiProviderCatalog(
                "openrouter",
                "OpenRouter",
                "https://openrouter.ai/api",
                null,
                null,
                "/v1/models",
                "openai/gpt-4o-mini",
                "",
                List.of(
                        new AiProviderModelDTO("openai/gpt-4o-mini", "OpenAI GPT-4o Mini", "chat", true),
                        new AiProviderModelDTO("anthropic/claude-3.7-sonnet", "Claude 3.7 Sonnet", "chat", false),
                        new AiProviderModelDTO("google/gemini-2.5-flash", "Gemini 2.5 Flash", "chat", false)
                ),
                List.of()
        ));
        providers.put("groq", new AiProviderCatalog(
                "groq",
                "Groq",
                "https://api.groq.com/openai",
                null,
                null,
                null,
                "openai/gpt-oss-20b",
                "",
                List.of(
                        new AiProviderModelDTO("openai/gpt-oss-20b", "OpenAI GPT OSS 20B", "chat", true),
                        new AiProviderModelDTO("openai/gpt-oss-120b", "OpenAI GPT OSS 120B", "chat", false),
                        new AiProviderModelDTO("qwen/qwen3-32b", "Qwen3 32B", "chat", false)
                ),
                List.of()
        ));
        return providers;
    }

}
