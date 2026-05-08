package com.agenticrag.user.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AiSettingsVerifyResponse {

    private Boolean success;

    private String message;

    private String provider;

    private LocalDateTime verifiedAt;

    private List<AiProviderModelDTO> chatModels;

    private List<AiProviderModelDTO> embeddingModels;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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
