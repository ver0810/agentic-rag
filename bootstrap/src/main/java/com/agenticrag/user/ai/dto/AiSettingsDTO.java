package com.agenticrag.user.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AiSettingsDTO {

    private String provider;

    private String providerName;

    private Boolean hasApiKey;

    private String apiKeyMasked;

    private Boolean verified;

    private LocalDateTime lastVerifiedAt;

    private String chatModel;

    private String embeddingModel;

    private List<AiProviderModelDTO> availableChatModels;

    private List<AiProviderModelDTO> availableEmbeddingModels;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public Boolean getHasApiKey() {
        return hasApiKey;
    }

    public void setHasApiKey(Boolean hasApiKey) {
        this.hasApiKey = hasApiKey;
    }

    public String getApiKeyMasked() {
        return apiKeyMasked;
    }

    public void setApiKeyMasked(String apiKeyMasked) {
        this.apiKeyMasked = apiKeyMasked;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<AiProviderModelDTO> getAvailableChatModels() {
        return availableChatModels;
    }

    public void setAvailableChatModels(List<AiProviderModelDTO> availableChatModels) {
        this.availableChatModels = availableChatModels;
    }

    public List<AiProviderModelDTO> getAvailableEmbeddingModels() {
        return availableEmbeddingModels;
    }

    public void setAvailableEmbeddingModels(List<AiProviderModelDTO> availableEmbeddingModels) {
        this.availableEmbeddingModels = availableEmbeddingModels;
    }
}
