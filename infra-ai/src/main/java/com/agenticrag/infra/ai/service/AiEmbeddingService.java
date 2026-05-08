package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import java.util.List;

public interface AiEmbeddingService {

    float[] embed(String text);

    float[] embed(String text, AiRuntimeOptions runtimeOptions);

    List<float[]> embedAll(List<String> texts);

    List<float[]> embedAll(List<String> texts, AiRuntimeOptions runtimeOptions);
}
