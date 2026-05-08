package com.agenticrag.infra.ai.service.impl;

import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.service.AiEmbeddingService;
import com.agenticrag.infra.ai.service.OpenAiCompatibleModelFactory;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiEmbeddingService implements AiEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final OpenAiCompatibleModelFactory modelFactory;

    public DefaultAiEmbeddingService(EmbeddingModel embeddingModel,
                                     OpenAiCompatibleModelFactory modelFactory) {
        this.embeddingModel = embeddingModel;
        this.modelFactory = modelFactory;
    }

    @Override
    public float[] embed(String text) {
        return embed(text, null);
    }

    @Override
    public float[] embed(String text, AiRuntimeOptions runtimeOptions) {
        if (runtimeOptions == null) {
            return embeddingModel.embed(text);
        }
        return modelFactory.createEmbeddingModel(runtimeOptions).embed(text);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        return embedAll(texts, null);
    }

    @Override
    public List<float[]> embedAll(List<String> texts, AiRuntimeOptions runtimeOptions) {
        if (runtimeOptions == null) {
            return embeddingModel.embed(texts);
        }
        return modelFactory.createEmbeddingModel(runtimeOptions).embed(texts);
    }
}
