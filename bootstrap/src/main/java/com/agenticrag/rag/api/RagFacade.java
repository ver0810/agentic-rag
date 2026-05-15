package com.agenticrag.rag.api;

import com.agenticrag.chat.dto.ChatEvent;
import com.agenticrag.rag.query.RagQueryResult;
import reactor.core.publisher.Flux;

public interface RagFacade {

    RagQueryResult query(RagQueryRequest request);

    Flux<ChatEvent> streamQuery(RagQueryRequest request);
}
