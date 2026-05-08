package com.agenticrag.infra.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agenticrag.ai")
public class AiProviderProperties {

    private String defaultProvider = "openai";
    private Map<String, ProviderConfig> providers = new HashMap<>();

    @PostConstruct
    public void validate() {
        if (!providers.containsKey(defaultProvider)) {
            throw new IllegalStateException("Default provider '" + defaultProvider + "' is not configured in providers map");
        }
        providers.forEach((name, config) -> {
            Assert.hasText(config.getBaseUrl(), "Provider '" + name + "' baseUrl cannot be empty");
            Assert.hasText(config.getApiKey(), "Provider '" + name + "' apiKey cannot be empty");
        });
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    public ProviderConfig getProvider(String provider) {
        Assert.hasText(provider, "Provider name cannot be empty");
        ProviderConfig config = providers.get(provider);
        if (config == null) {
            throw new IllegalArgumentException("No configuration found for provider: " + provider + 
                ". Available providers: " + providers.keySet());
        }
        return config;
    }

    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private String chatModel;
        private String embeddingModel;
        private String completionsPath;
        private String embeddingsPath;
        private String modelsPath;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
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

        public String getCompletionsPath() {
            return completionsPath;
        }

        public void setCompletionsPath(String completionsPath) {
            this.completionsPath = completionsPath;
        }

        public String getEmbeddingsPath() {
            return embeddingsPath;
        }

        public void setEmbeddingsPath(String embeddingsPath) {
            this.embeddingsPath = embeddingsPath;
        }

        public String getModelsPath() {
            return modelsPath;
        }

        public void setModelsPath(String modelsPath) {
            this.modelsPath = modelsPath;
        }
    }
}