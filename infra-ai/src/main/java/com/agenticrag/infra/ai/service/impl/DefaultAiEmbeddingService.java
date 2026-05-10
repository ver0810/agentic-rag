package com.agenticrag.infra.ai.service.impl;

import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.service.AiEmbeddingService;
import com.agenticrag.infra.ai.service.AiProviderRouter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultAiEmbeddingService implements AiEmbeddingService {
    private static final int MAX_EMBED_BATCH_SIZE = 10;

    private final EmbeddingModel embeddingModel;
    private final AiProviderRouter providerRouter;

    public DefaultAiEmbeddingService(EmbeddingModel embeddingModel,
                                     AiProviderRouter providerRouter) {
        this.embeddingModel = embeddingModel;
        this.providerRouter = providerRouter;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    public float[] embed(String text, AiRuntimeContext context) {
        return selectEmbeddingModel(context).embed(text);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        return embedAll(texts, null);
    }

    @Override
    public List<float[]> embedAll(List<String> texts, AiRuntimeContext context) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingModel model = selectEmbeddingModel(context);
        List<float[]> results = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_EMBED_BATCH_SIZE) {
            int end = Math.min(start + MAX_EMBED_BATCH_SIZE, texts.size());
            results.addAll(model.embed(texts.subList(start, end)));
        }
        return results;
    }

    private EmbeddingModel selectEmbeddingModel(AiRuntimeContext context) {
        if (context == null) {
            return embeddingModel;
        }
        return providerRouter.createEmbeddingModel(context.getOptions());
    }
}
