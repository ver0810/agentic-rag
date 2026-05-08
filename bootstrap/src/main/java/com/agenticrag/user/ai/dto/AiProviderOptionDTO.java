package com.agenticrag.user.ai.dto;

public class AiProviderOptionDTO {

    private String provider;

    private String displayName;

    private Boolean configured;

    private Boolean verified;

    private Boolean enabled;

    public AiProviderOptionDTO() {
    }

    public AiProviderOptionDTO(String provider, String displayName) {
        this.provider = provider;
        this.displayName = displayName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getConfigured() {
        return configured;
    }

    public void setConfigured(Boolean configured) {
        this.configured = configured;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
