package com.agenticrag.infra.ai.model;

/**
 * AI 运行时配置基类
 */
public abstract class AiRuntimeOptions {

    private String provider;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 获取聊天模型标识
     */
    public abstract String getChatModel();

    /**
     * 获取向量模型标识
     */
    public abstract String getEmbeddingModel();
}
