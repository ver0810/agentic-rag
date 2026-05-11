package com.agenticrag.infrastructure.embedding;

import com.agenticrag.infra.ai.config.EmbeddingProperties;
import com.agenticrag.infra.ai.model.OpenAiRuntimeOptions;
import com.agenticrag.infra.ai.port.embedding.KnowledgeEmbeddingPort;
import com.agenticrag.infra.ai.service.AiProviderRouter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KnowledgeEmbeddingPortAdapter implements KnowledgeEmbeddingPort {
    private static final int MAX_EMBED_BATCH_SIZE = 10;

    private final EmbeddingProperties embeddingProperties;
    private final AiProviderRouter providerRouter;
    private EmbeddingModel embeddingModel;

    public KnowledgeEmbeddingPortAdapter(EmbeddingProperties embeddingProperties,
                                         AiProviderRouter providerRouter) {
        this.embeddingProperties = embeddingProperties;
        this.providerRouter = providerRouter;
    }

    @PostConstruct
    public void init() {
        try {
            OpenAiRuntimeOptions options = new OpenAiRuntimeOptions();
            options.setProvider(embeddingProperties.getProvider());
            options.setBaseUrl(embeddingProperties.getBaseUrl());
            options.setApiKey(embeddingProperties.getApiKey());
            options.setEmbeddingModel(embeddingProperties.getModel());

            this.embeddingModel = providerRouter.createEmbeddingModel(options);
            log.info("Knowledge embedding service initialized with provider: {}, model: {}",
                    embeddingProperties.getProvider(), embeddingProperties.getModel());
        } catch (Exception e) {
            log.warn("Failed to initialize knowledge embedding service: {}. Embedding will be unavailable.", e.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        if (embeddingModel == null) {
            throw new RuntimeException("Knowledge embedding service is not available. Please check embedding configuration.");
        }
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (embeddingModel == null) {
            throw new RuntimeException("Knowledge embedding service is not available. Please check embedding configuration.");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> results = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_EMBED_BATCH_SIZE) {
            int end = Math.min(start + MAX_EMBED_BATCH_SIZE, texts.size());
            results.addAll(embeddingModel.embed(texts.subList(start, end)));
        }
        return results;
    }
}
