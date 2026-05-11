package com.agenticrag.infra.ai.api.rag;

import com.agenticrag.infra.ai.rag.query.RagQueryResult;

public interface RagFacade {

    RagQueryResult query(RagQueryRequest request);
}
