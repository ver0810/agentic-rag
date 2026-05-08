package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * AI 模型工厂接口，支持多提供商扩展
 */
public interface AiModelFactory {

    /**
     * 判断是否支持指定的提供商
     * @param provider 提供商标识
     * @return 是否支持
     */
    boolean supports(String provider);

    /**
     * 获取支持的提供商标识
     * @return 提供商标识
     */
    String getProvider();

    /**
     * 创建聊天模型
     * @param options 运行时选项
     * @param chatOptions 聊天选项
     * @return 聊天模型
     */
    ChatModel createChatModel(AiRuntimeOptions options, ChatOptions chatOptions);

    /**
     * 创建嵌入模型
     * @param options 运行时选项
     * @return 嵌入模型
     */
    EmbeddingModel createEmbeddingModel(AiRuntimeOptions options);
}