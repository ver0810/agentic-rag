package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.model.AiRuntimeContext;
import java.util.List;

public interface AiEmbeddingService {

    float[] embed(String text);

    float[] embed(String text, AiRuntimeContext context);

    List<float[]> embedAll(List<String> texts);

    List<float[]> embedAll(List<String> texts, AiRuntimeContext context);
}
