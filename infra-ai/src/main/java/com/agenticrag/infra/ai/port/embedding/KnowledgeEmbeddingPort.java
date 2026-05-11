package com.agenticrag.infra.ai.port.embedding;

import java.util.List;

public interface KnowledgeEmbeddingPort {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
