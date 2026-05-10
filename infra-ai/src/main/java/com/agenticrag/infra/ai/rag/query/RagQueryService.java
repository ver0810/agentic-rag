package com.agenticrag.infra.ai.rag.query;

import com.agenticrag.infra.ai.model.AiRuntimeContext;

import java.util.List;

public interface RagQueryService {

    String query(String query, String kbId, String userId);

    String query(String query, String kbId, String userId, int topK);

    String query(String query, String kbId, String userId, AiRuntimeContext context, int topK);
}
