package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.config.AiProviderProperties;
import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.model.OpenAiRuntimeOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiProviderRouter {

    private final Map<String, AiModelFactory> factories;
    private final List<AiModelFactory> factoryList;
    private final AiProviderProperties providerProperties;
    private final AiModelFactory defaultFactory;

    public AiProviderRouter(List<AiModelFactory> factoryList, AiProviderProperties providerProperties) {
        Assert.notEmpty(factoryList, "At least one AiModelFactory implementation is required");
        this.providerProperties = providerProperties;
        this.factoryList = factoryList;
        this.factories = factoryList.stream()
                .collect(Collectors.toMap(AiModelFactory::getProvider, Function.identity()));
        this.defaultFactory = factories.get(providerProperties.getDefaultProvider());
        if (this.defaultFactory == null) {
            throw new IllegalStateException("No factory found for default provider: " + providerProperties.getDefaultProvider());
        }
    }

    public ChatModel createChatModel(AiRuntimeOptions options, ChatOptions chatOptions) {
        Assert.notNull(options, "AiRuntimeOptions cannot be null");
        AiModelFactory factory = resolveFactory(options.getProvider());
        return factory.createChatModel(options, chatOptions);
    }

    public EmbeddingModel createEmbeddingModel(AiRuntimeOptions options) {
        Assert.notNull(options, "AiRuntimeOptions cannot be null");
        AiModelFactory factory = resolveFactory(options.getProvider());
        return factory.createEmbeddingModel(options);
    }

    private AiModelFactory resolveFactory(String provider) {
        if (provider == null || provider.isBlank()) {
            return defaultFactory;
        }
        
        // 1. Exact match by provider key
        AiModelFactory factory = factories.get(provider);
        if (factory != null) {
            return factory;
        }

        // 2. Flexible match using supports() method
        for (AiModelFactory f : factoryList) {
            if (f.supports(provider)) {
                return f;
            }
        }
        
        throw new IllegalArgumentException("Unsupported AI provider: " + provider + 
            ". Supported providers: " + factories.keySet());
    }
}