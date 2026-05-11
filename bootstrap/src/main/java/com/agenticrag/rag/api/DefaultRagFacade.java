package com.agenticrag.rag.api;

import com.agenticrag.rag.query.RagQueryResult;
import com.agenticrag.rag.query.RagQueryService;
import org.springframework.stereotype.Service;

@Service
public class DefaultRagFacade implements RagFacade {

    private final RagQueryService ragQueryService;

    public DefaultRagFacade(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @Override
    public RagQueryResult query(RagQueryRequest request) {
        int topK = request.topK() != null ? request.topK() : 5;
        return ragQueryService.queryDetailed(
                request.query(),
                request.knowledgeBaseId(),
                request.userId(),
                request.runtimeContext(),
                topK);
    }
}
