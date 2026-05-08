package com.agenticrag.infra.ai.service.impl;

import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.service.AiEmbeddingService;
import com.agenticrag.infra.ai.service.AiProviderRouter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultAiEmbeddingService implements AiEmbeddingService {

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
        return embeddingModel.embed(texts);
    }

    @Override
    public List<float[]> embedAll(List<String> texts, AiRuntimeContext context) {
        return selectEmbeddingModel(context).embed(texts);
    }

    private EmbeddingModel selectEmbeddingModel(AiRuntimeContext context) {
        if (context == null) {
            return embeddingModel;
        }
        return providerRouter.createEmbeddingModel(context.getOptions());
    }
}
