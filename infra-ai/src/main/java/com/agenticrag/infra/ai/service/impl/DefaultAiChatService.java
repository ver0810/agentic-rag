package com.agenticrag.infra.ai.service.impl;

import com.agenticrag.infra.ai.config.AiChatProperties;
import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.infra.ai.service.AiProviderRouter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DefaultAiChatService implements AiChatService {

    private final ChatModel chatModel;
    private final AiChatProperties properties;
    private final AiProviderRouter providerRouter;
    private final Map<String, ChatClient> chatClients = new ConcurrentHashMap<>();

    public DefaultAiChatService(ChatModel chatModel,
                                AiChatProperties properties,
                                AiProviderRouter providerRouter, ChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.providerRouter = providerRouter;
    }

    @Override
    public String call(AiChatScene scene, String message, AiRuntimeContext context, String conversationId) {
        String enhancedMessage = applyPreEnhancements(message, context);
        ChatClient chatClient = chatClients.computeIfAbsent(conversationId, id -> buildChatClient(scene, context, id));
        String result = chatClient.prompt()
                .user(enhancedMessage)
                .call()
                .content();
        return applyPostEnhancements(result, context);
    }

    @Override
    public Flux<String> stream(AiChatScene scene, String message, AiRuntimeContext context, String conversationId) {
        String enhancedMessage = applyPreEnhancements(message, context);
        ChatClient chatClient = chatClients.computeIfAbsent(conversationId, id -> buildChatClient(scene, context, id));
        return chatClient.prompt()
                .user(enhancedMessage)
                .stream()
                .content();
    }

    private String applyPreEnhancements(String message, AiRuntimeContext context) {
        if (context != null && context.getEnhancement().getProjectContext() != null) {
            return "Project Context: " + context.getEnhancement().getProjectContext() + "\n\nUser Message: " + message;
        }
        return message;
    }

    private String applyPostEnhancements(String result, AiRuntimeContext context) {
        // TODO: Implement post-processing like Guardian AI audit if enabled
        return result;
    }

    private ChatClient buildChatClient(AiChatScene scene, AiRuntimeContext context, String conversationId) {
        ChatModel selectedModel = selectChatModel(context, scene);
        AiChatProperties.SceneOptions sceneOptions = properties.getScenes().get(resolveSceneCode(scene));

        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(10).build();

        ChatClient.Builder builder = ChatClient.builder(selectedModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultAdvisors(advisor ->
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationId)
                )
                .defaultOptions(buildOptions(sceneOptions, context));

        if (sceneOptions != null && hasText(sceneOptions.getSystemPrompt())) {
            builder.defaultSystem(sceneOptions.getSystemPrompt());
        }

        return builder.build();
    }

    private ChatOptions buildOptions(AiChatProperties.SceneOptions sceneOptions, AiRuntimeContext context) {
        ChatOptions defaultOptions = chatModel.getDefaultOptions();
        OpenAiChatOptions options = defaultOptions instanceof OpenAiChatOptions openAiChatOptions
                ? OpenAiChatOptions.fromOptions(openAiChatOptions)
                : OpenAiChatOptions.builder().build();

        if (context != null && hasText(context.getOptions().getChatModel())) {
            options.setModel(context.getOptions().getChatModel());
        }
        if (sceneOptions == null) {
            options.setStreamUsage(false);
            return options;
        }
        if (hasText(sceneOptions.getModel())) {
            options.setModel(sceneOptions.getModel());
        }
        if (sceneOptions.getTemperature() != null) {
            options.setTemperature(sceneOptions.getTemperature());
        }
        if (sceneOptions.getMaxTokens() != null) {
            options.setMaxTokens(sceneOptions.getMaxTokens());
        }
        options.setStreamUsage(false);
        return options;
    }

    private ChatModel selectChatModel(AiRuntimeContext context, AiChatScene scene) {
        if (context == null) {
            return chatModel;
        }
        ChatOptions options = buildOptions(
                properties.getScenes().get(resolveSceneCode(scene)),
                context
        );
        return providerRouter.createChatModel(context.getOptions(), options);
    }

    private String resolveSceneCode(AiChatScene scene) {
        if (scene != null) {
            return scene.code();
        }
        return properties.getDefaultScene();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
