package com.agenticrag.rag.query;

import com.agenticrag.chat.dto.ChatEvent;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import reactor.core.publisher.Flux;

public interface RagQueryService {

    String query(String query, String kbId, String userId);

    String query(String query, String kbId, String userId, int topK);

    String query(String query, String kbId, String userId, AiRuntimeContext context, int topK);

    RagQueryResult queryDetailed(String query, String kbId, String userId, int topK);

    RagQueryResult queryDetailed(String query, String kbId, String userId, AiRuntimeContext context, int topK);

    RagQueryResult queryDetailed(String query, String kbId, String userId, AiRuntimeContext context, String conversationId, int topK);

    Flux<ChatEvent> streamQueryDetailed(String query, String kbId, String userId, AiRuntimeContext context, String conversationId, int topK);
}
