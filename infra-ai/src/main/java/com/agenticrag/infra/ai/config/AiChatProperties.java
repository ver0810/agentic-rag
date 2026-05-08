package com.agenticrag.infra.ai.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agenticrag.ai.chat")
public class AiChatProperties {

    private String defaultScene = "general";

    private Map<String, SceneOptions> scenes = new LinkedHashMap<>();

    public String getDefaultScene() {
        return defaultScene;
    }

    public void setDefaultScene(String defaultScene) {
        this.defaultScene = defaultScene;
    }

    public Map<String, SceneOptions> getScenes() {
        return scenes;
    }

    public void setScenes(Map<String, SceneOptions> scenes) {
        this.scenes = scenes;
    }

    public static class SceneOptions {

        private String model;
        private Double temperature;
        private Integer maxTokens;
        private String systemPrompt;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }
}
