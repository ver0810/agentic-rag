package com.agenticrag.user.ai.dto;

import java.util.List;

public class AiProviderCatalog {

    private String provider;

    private String displayName;

    private String baseUrl;

    private String completionsPath;

    private String embeddingsPath;

    private String modelsPath;

    private String defaultChatModel;

    private String defaultEmbeddingModel;

    private List<AiProviderModelDTO> chatModels;

    private List<AiProviderModelDTO> embeddingModels;

    public AiProviderCatalog(String provider,
                             String displayName,
                             String baseUrl,
                             String completionsPath,
                             String embeddingsPath,
                             String modelsPath,
                             String defaultChatModel,
                             String defaultEmbeddingModel,
                             List<AiProviderModelDTO> chatModels,
                             List<AiProviderModelDTO> embeddingModels) {
        this.provider = provider;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.completionsPath = completionsPath;
        this.embeddingsPath = embeddingsPath;
        this.modelsPath = modelsPath;
        this.defaultChatModel = defaultChatModel;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
        this.chatModels = chatModels;
        this.embeddingModels = embeddingModels;
    }

    public String getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getCompletionsPath() {
        return completionsPath;
    }

    public String getEmbeddingsPath() {
        return embeddingsPath;
    }

    public String getModelsPath() {
        return modelsPath;
    }

    public String getDefaultChatModel() {
        return defaultChatModel;
    }

    public String getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public List<AiProviderModelDTO> getChatModels() {
        return chatModels;
    }

    public List<AiProviderModelDTO> getEmbeddingModels() {
        return embeddingModels;
    }
}
