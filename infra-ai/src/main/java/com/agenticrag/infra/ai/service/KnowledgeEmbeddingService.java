package com.agenticrag.infra.ai.service;

import java.util.List;

public interface KnowledgeEmbeddingService {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
