package com.agenticrag.infra.ai.rag.query;

import java.util.List;

public interface RagQueryService {

    String query(String query, String kbId, String userId);

    String query(String query, String kbId, String userId, int topK);
}
