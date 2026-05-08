package com.agenticrag.infra.ai.service.impl;

import com.agenticrag.infra.ai.config.AiChatProperties;
import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.infra.ai.service.OpenAiCompatibleModelFactory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class DefaultAiChatService implements AiChatService {

    private final ChatModel chatModel;
    private final AiChatProperties properties;
    private final OpenAiCompatibleModelFactory modelFactory;

    public DefaultAiChatService(ChatModel chatModel,
                                AiChatProperties properties,
                                OpenAiCompatibleModelFactory modelFactory) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.modelFactory = modelFactory;
    }

    @Override
    public String call(AiChatScene scene, String message) {
        return call(scene, message, null);
    }

    @Override
    public String call(AiChatScene scene, String message, AiRuntimeOptions runtimeOptions) {
        ChatModel selectedModel = selectChatModel(runtimeOptions, scene);
        ChatResponse response = selectedModel.call(buildPrompt(scene, message, runtimeOptions));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    @Override
    public Flux<String> stream(AiChatScene scene, String message) {
        return stream(scene, message, null);
    }

    @Override
    public Flux<String> stream(AiChatScene scene, String message, AiRuntimeOptions runtimeOptions) {
        ChatModel selectedModel = selectChatModel(runtimeOptions, scene);
        return selectedModel.stream(buildPrompt(scene, message, runtimeOptions))
                .map(this::extractText)
                .filter(text -> text != null && !text.isEmpty());
    }

    private Prompt buildPrompt(AiChatScene scene, String message, AiRuntimeOptions runtimeOptions) {
        AiChatProperties.SceneOptions sceneOptions = properties.getScenes().get(resolveSceneCode(scene));
        ChatOptions options = buildOptions(sceneOptions, runtimeOptions);
        List<Message> messages = new ArrayList<>();
        if (sceneOptions != null && hasText(sceneOptions.getSystemPrompt())) {
            messages.add(new SystemMessage(sceneOptions.getSystemPrompt()));
        }
        messages.add(new UserMessage(message));
        return new Prompt(messages, options);
    }

    private ChatOptions buildOptions(AiChatProperties.SceneOptions sceneOptions, AiRuntimeOptions runtimeOptions) {
        ChatOptions defaultOptions = chatModel.getDefaultOptions();
        OpenAiChatOptions options = defaultOptions instanceof OpenAiChatOptions openAiChatOptions
                ? OpenAiChatOptions.fromOptions(openAiChatOptions)
                : OpenAiChatOptions.builder().build();

        if (runtimeOptions != null && hasText(runtimeOptions.getChatModel())) {
            options.setModel(runtimeOptions.getChatModel());
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

    private ChatModel selectChatModel(AiRuntimeOptions runtimeOptions, AiChatScene scene) {
        if (runtimeOptions == null) {
            return chatModel;
        }
        OpenAiChatOptions options = (OpenAiChatOptions) buildOptions(
                properties.getScenes().get(resolveSceneCode(scene)),
                runtimeOptions
        );
        if (chatModel instanceof OpenAiChatModel) {
            return modelFactory.createChatModel(runtimeOptions, options);
        }
        return chatModel;
    }

    private String resolveSceneCode(AiChatScene scene) {
        if (scene != null) {
            return scene.code();
        }
        return properties.getDefaultScene();
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
